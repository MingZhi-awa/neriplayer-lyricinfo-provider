/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import android.media.MediaMetadata
import android.media.session.MediaSession
import java.lang.ref.WeakReference

/**
 * Remembers which MediaSession currently owns the notification, and the exact metadata object the
 * host published for it.
 *
 * Replaying a resolved lyric means asking the host session to publish that same metadata object
 * again, so the reference must be weak: the host may drop a session at any time and a strong
 * reference would leak the whole media stack of a killed session.
 */
internal class NeriSessionRegistry {

    private val lock = Any()
    private var sessionRef: WeakReference<MediaSession>? = null
    private var metadataRef: WeakReference<MediaMetadata>? = null

    fun onConstructed(session: MediaSession) {
        synchronized(lock) {
            sessionRef = WeakReference(session)
        }
    }

    fun onMetadata(session: MediaSession, metadata: MediaMetadata) {
        synchronized(lock) {
            sessionRef = WeakReference(session)
            metadataRef = WeakReference(metadata)
        }
    }

    /** Playback-state traffic proves the session is still the active one, without touching metadata. */
    fun onMetadataStatePing(session: MediaSession) {
        synchronized(lock) {
            if (sessionRef?.get() !== session) sessionRef = WeakReference(session)
        }
    }

    fun onReleased(session: MediaSession) {
        synchronized(lock) {
            if (sessionRef?.get() === session) {
                sessionRef = null
                metadataRef = null
            }
        }
    }

    fun activeSession(): MediaSession? = synchronized(lock) { sessionRef?.get() }

    fun latestMetadata(): MediaMetadata? = synchronized(lock) { metadataRef?.get() }

    fun latestMetadataOf(session: MediaSession): MediaMetadata? = synchronized(lock) {
        if (sessionRef?.get() !== session) return null
        metadataRef?.get()
    }

    fun forgetAll() {
        synchronized(lock) {
            sessionRef = null
            metadataRef = null
        }
    }
}
