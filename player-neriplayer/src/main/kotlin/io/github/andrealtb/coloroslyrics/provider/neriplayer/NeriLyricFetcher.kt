/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.andrealtb.coloroslyrics.provider.core.publisher.TtmlPayloadNormalizer
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.LrcParser
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import io.github.andrealtb.coloroslyrics.provider.parser.ttml.TtmlParser

/**
 * Resolves the authoritative lyric document for one playing track.
 *
 * NeriPlayer is open source and can publish `lyricInfo` itself, but that path is a user toggle
 * that also performs privileged SystemUI configuration. When the toggle is off the host process
 * holds no publishable payload at all, so this Provider resolves the same document the player
 * would: AMLL TTML first (word timing, duet sides, translations), then LRCLIB lines.
 *
 * Results are cached per stable track key and never fetched twice for the same track while the
 * process lives. All work happens on the caller's background thread.
 */
internal class NeriLyricFetcher {

    data class Bundle(
        val trackKey: String,
        val lines: List<RichLyricLine>,
        val ttml: String?,
        val source: String,
        val estimatedDurationMs: Long
    )

    private val cache = object : LinkedHashMap<String, Bundle>(NeriPlayerConstants.MAX_CACHED_TRACKS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bundle>?): Boolean =
            size > NeriPlayerConstants.MAX_CACHED_TRACKS
    }

    /** Tracks that produced neither a TTML nor a synced LRC document; they are not retried. */
    private val misses = object : LinkedHashMap<String, Boolean>(NeriPlayerConstants.MAX_CACHED_TRACKS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > NeriPlayerConstants.MAX_CACHED_TRACKS
    }

    fun trackKeyOf(track: TrackIdentity): String = track.buildStableKey()

    @Synchronized
    fun cached(key: String): Bundle? = cache[key]

    @Synchronized
    fun isKnownMiss(key: String): Boolean = misses.containsKey(key)

    fun fetch(track: TrackIdentity): Bundle? {
        val trackKey = trackKeyOf(track)
        val title = track.title?.takeIf(String::isNotBlank) ?: return null
        val artist = track.artist.orEmpty()
        val durationMs = track.durationMs

        val fromAmll = fetchFromAmll(trackKey, title, artist, durationMs)
        if (fromAmll != null) {
            store(fromAmll)
            return fromAmll
        }
        val fromLrclib = fetchFromLrclib(trackKey, title, artist, durationMs)
        if (fromLrclib != null) {
            store(fromLrclib)
            return fromLrclib
        }
        synchronized(this) { misses[trackKey] = true }
        return null
    }

    private fun fetchFromAmll(trackKey: String, title: String, artist: String, durationMs: Long): Bundle? {
        val candidates = NeriAmllClient.search(title, artist)
        if (candidates.isEmpty()) return null
        for (candidate in candidates) {
            val document = NeriAmllClient.fetchDocument(candidate.file) ?: continue
            val parsed = TtmlParser.parse(document) ?: continue
            if (parsed.lines.isEmpty()) continue
            val lines = NeriTtmlTranslations.apply(parsed.lines, NeriTtmlTranslations.extract(document))
            val estimated = estimateDurationMs(lines)
            if (!isDurationAccepted(durationMs, estimated)) continue
            return Bundle(
                trackKey = trackKey,
                lines = lines,
                ttml = TtmlPayloadNormalizer.normalize(document),
                source = "amll",
                estimatedDurationMs = estimated
            )
        }
        return null
    }

    private fun fetchFromLrclib(trackKey: String, title: String, artist: String, durationMs: Long): Bundle? {
        val synced = NeriLrclibClient.fetchSyncedLyrics(title, artist, durationMs) ?: return null
        val document = LrcParser.parse(synced, durationMs)
        if (document.lines.isEmpty()) return null
        val lines = document.lines.mapIndexed { index, line ->
            val nextStart = document.lines.getOrNull(index + 1)?.begin
            val end = when {
                line.end > line.begin -> line.end
                nextStart != null && nextStart > line.begin -> nextStart
                else -> line.begin + 4_000L
            }
            RichLyricLine(begin = line.begin, end = end, text = line.text.orEmpty())
        }.filter { it.text?.isNotBlank() == true }
        if (lines.isEmpty()) return null
        val estimated = estimateDurationMs(lines)
        if (!isDurationAccepted(durationMs, estimated)) return null
        return Bundle(
            trackKey = trackKey,
            lines = lines,
            ttml = null,
            source = "lrclib",
            estimatedDurationMs = estimated
        )
    }

    @Synchronized
    private fun store(bundle: Bundle) {
        cache[bundle.trackKey] = bundle
        misses.remove(bundle.trackKey)
    }

    /**
     * The last line start plus its own end delta underestimates a trailing instrumental outro,
     * which is fine: the check only rejects documents that clearly belong to another edit.
     */
    private fun estimateDurationMs(lines: List<RichLyricLine>): Long {
        val last = lines.lastOrNull() ?: return 0L
        val tail = (last.end - last.begin).coerceIn(0L, 30_000L)
        return last.begin + tail
    }

    private fun isDurationAccepted(expectedMs: Long, estimatedMs: Long): Boolean {
        if (expectedMs <= 0L || estimatedMs <= 0L) return true
        return kotlin.math.abs(estimatedMs - expectedMs) <= NeriPlayerConstants.DURATION_TOLERANCE_MS ||
            estimatedMs <= expectedMs
    }
}
