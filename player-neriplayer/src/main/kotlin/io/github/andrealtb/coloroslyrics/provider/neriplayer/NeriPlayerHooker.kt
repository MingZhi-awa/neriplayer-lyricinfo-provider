/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderDebugConfig
import io.github.andrealtb.coloroslyrics.provider.core.config.ProviderId
import io.github.andrealtb.coloroslyrics.provider.core.diagnostics.DiagnosticHasher
import io.github.andrealtb.coloroslyrics.provider.core.mode.RuntimeModeResolver
import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.andrealtb.coloroslyrics.provider.core.policy.TrackGenerationPolicy
import io.github.andrealtb.coloroslyrics.provider.core.policy.TrackIdentityPolicy
import io.github.andrealtb.coloroslyrics.provider.core.publisher.HostMetadataOverlay
import io.github.andrealtb.coloroslyrics.provider.hook102.ProviderHookContext
import java.util.concurrent.Executors

/**
 * Installs the whole Provider on NeriPlayer's main process.
 *
 * Only framework entry points are hooked, because the release build of the host is R8-obfuscated
 * and every `moe.ouom.neriplayer` internal name except manifest-referenced classes is gone:
 * `MediaSession#setMetadata` / `#setPlaybackState` / `#setActive` / `#release` are stable, and the
 * host is identified by package and main-process name before anything is installed.
 *
 * The Provider never rebuilds host metadata. It writes its single `lyricInfo` key into the exact
 * object the host built, so artwork, URIs, ratings and unknown keys reach SystemUI untouched.
 */
class NeriPlayerHooker(private val hookContext: ProviderHookContext) {

    private val hookRuntime = hookContext.runtime
    private val hostContext = hookContext.application
    private val sessions = NeriSessionRegistry()
    private val generationPolicy = TrackGenerationPolicy()
    private val fetcher = NeriLyricFetcher()
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "neri-lyric-fetch").apply { isDaemon = true }
    }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val moduleWrite = ThreadLocal.withInitial { false }

    @Volatile
    private var inFlightTrackKey: String? = null

    fun onHook() {
        val resolution = RuntimeModeResolver.resolve(hostContext)
        if (!resolution.mode.isSupported) {
            NeriDiagnostics.warning(
                area = "bootstrap",
                event = "HOOK_DISABLED",
                reason = "mode=${resolution.mode.name.lowercase()} process=${resolution.processName} marker=${resolution.markerSource}"
            )
            return
        }
        val debug = ProviderDebugConfig.applyDiagnostics(
            mode = resolution.mode,
            provider = ProviderId.NERIPLAYER,
            rootSource = hookContext.debugSource,
            frameworkSink = hookContext.frameworkSink
        )
        NeriDiagnostics.info(
            area = "bootstrap",
            event = "DEBUG_CONFIG_APPLIED",
            reason = debug.reason
        )
        installSessionHooks()
        NeriDiagnostics.info(area = "bootstrap", event = "PROCESS_READY", reason = hostContext.packageName)
    }

    private fun installSessionHooks() {
        runCatching {
            val type = MediaSession::class.java
            type.declaredConstructors.forEachIndexed { index, constructor ->
                hookRuntime.hook(constructor, "neri.session#ctor$index") {
                    after {
                        (instanceOrNull as? MediaSession)?.let(sessions::onConstructed)
                    }
                }
            }
            hookRuntime.hook(
                type.getDeclaredMethod("setMetadata", MediaMetadata::class.java),
                "neri.session#setMetadata"
            ) {
                before {
                    if (moduleWrite.get() == true) return@before
                    val session = instanceOrNull as? MediaSession ?: return@before
                    val metadata = args.getOrNull(0) as? MediaMetadata ?: return@before
                    runCatching { onHostMetadata(session, metadata) }
                        .onFailure { NeriDiagnostics.failure("session", "METADATA_HANDLER_FAILED", it) }
                }
            }
            hookRuntime.hook(
                type.getDeclaredMethod("setPlaybackState", PlaybackState::class.java),
                "neri.session#setPlaybackState"
            ) {
                before {
                    (instanceOrNull as? MediaSession)?.let { session ->
                        sessions.onMetadataStatePing(session)
                    }
                }
            }
            hookRuntime.hook(
                type.getDeclaredMethod("setActive", Boolean::class.javaPrimitiveType),
                "neri.session#setActive"
            ) {
                before {
                    val session = instanceOrNull as? MediaSession ?: return@before
                    if (args.getOrNull(0) as? Boolean == true) sessions.onConstructed(session)
                }
            }
            hookRuntime.hook(type.getDeclaredMethod("release"), "neri.session#release") {
                before {
                    (instanceOrNull as? MediaSession)?.let(sessions::onReleased)
                }
            }
        }.onFailure { NeriDiagnostics.failure("session", "SESSION_HOOK_FAILED", it) }
    }

    private fun onHostMetadata(session: MediaSession, metadata: MediaMetadata) {
        val track = NeriTrackFrame.trackOf(metadata) ?: return
        val generation = generationPolicy.onTrackObserved(track)
        sessions.onMetadata(session, metadata)

        val trackKey = fetcher.trackKeyOf(track)
        val cached = fetcher.cached(trackKey)
        if (cached != null) {
            applyPayload(metadata, track, cached, generation)
            return
        }
        if (fetcher.isKnownMiss(trackKey)) return
        if (inFlightTrackKey == trackKey) return
        inFlightTrackKey = trackKey
        NeriDiagnostics.info(
            area = "lyrics",
            event = "LYRIC_FETCH_STARTED",
            generation = generation,
            trackHash = DiagnosticHasher.sha256(trackKey)
        )
        worker.execute {
            val bundle = runCatching { fetcher.fetch(track) }
                .onFailure { NeriDiagnostics.failure("lyrics", "LYRIC_FETCH_FAILED", it) }
                .getOrNull()
            mainHandler.post {
                inFlightTrackKey = null
                onFetched(track, generation, bundle)
            }
        }
    }

    private fun onFetched(track: TrackIdentity, generation: Long, bundle: NeriLyricFetcher.Bundle?) {
        if (bundle == null) {
            NeriDiagnostics.info(
                area = "lyrics",
                event = "LYRIC_FETCH_EMPTY",
                generation = generation,
                trackHash = DiagnosticHasher.sha256(track.buildStableKey())
            )
            return
        }
        if (!generationPolicy.isGenerationValid(generation)) {
            NeriDiagnostics.info(area = "lyrics", event = "LYRIC_FETCH_STALE", generation = generation)
            return
        }
        val current = generationPolicy.currentTrack ?: return
        if (!TrackIdentityPolicy.isSameTrack(current, track)) {
            NeriDiagnostics.info(area = "lyrics", event = "LYRIC_FETCH_TRACK_CHANGED", generation = generation)
            return
        }
        val session = sessions.activeSession() ?: return
        val metadata = sessions.latestMetadataOf(session) ?: return
        val liveTrack = NeriTrackFrame.trackOf(metadata) ?: return
        if (!TrackIdentityPolicy.isSameTrack(liveTrack, track)) return

        NeriDiagnostics.info(
            area = "lyrics",
            event = "LYRIC_FETCHED",
            generation = generation,
            trackHash = DiagnosticHasher.sha256(track.buildStableKey()),
            reason = "${bundle.source} lines=${bundle.lines.size} ttml=${bundle.ttml?.length ?: 0}"
        )
        applyPayload(metadata, track, bundle, generation)
        republish(session, metadata)
    }

    private fun applyPayload(
        metadata: MediaMetadata,
        track: TrackIdentity,
        bundle: NeriLyricFetcher.Bundle,
        generation: Long
    ) {
        val hostLyricInfo = runCatching { HostMetadataOverlay.hostLyricInfo(metadata) }.getOrNull()
        val outcome = NeriPayloadComposer.compose(
            track = track,
            lines = bundle.lines,
            ttml = bundle.ttml,
            generation = generation,
            playerPackage = hostContext.packageName,
            hostLyricInfo = hostLyricInfo,
            source = bundle.source
        ) ?: run {
            NeriDiagnostics.warning(area = "publisher", event = "PAYLOAD_NOT_COMPOSED", reason = bundle.source)
            return
        }
        val written = HostMetadataOverlay.putLyricInfo(metadata, outcome.json)
        NeriDiagnostics.info(
            area = "publisher",
            event = "LYRIC_INFO_${written.result.name}",
            generation = generation,
            trackHash = DiagnosticHasher.sha256(track.buildStableKey()),
            payloadChars = outcome.json.length,
            parcelBytes = written.parcelBytes,
            reason = "${bundle.source}${if (outcome.appendedToHostPayload) "+append" else ""}"
        )
    }

    /** Re-publishes the host's own metadata object so SystemUI re-reads the appended key. */
    private fun republish(session: MediaSession, metadata: MediaMetadata) {
        moduleWrite.set(true)
        try {
            session.setMetadata(metadata)
        } catch (error: Throwable) {
            NeriDiagnostics.failure("publisher", "REPUBLISH_FAILED", error)
        } finally {
            moduleWrite.set(false)
        }
    }
}
