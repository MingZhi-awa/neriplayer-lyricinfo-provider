/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import android.media.MediaMetadata
import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity

/**
 * Reads the identity NeriPlayer actually published, without touching any field.
 *
 * Identity priority follows the integration contract: stable media ID first, then
 * title + artist + duration. NeriPlayer sets `METADATA_KEY_MEDIA_ID` from its car media ID helper
 * only when the queue position and the song key agree, so it is a real per-track ID when present.
 */
object NeriTrackFrame {

    fun trackOf(metadata: MediaMetadata?): TrackIdentity? {
        if (metadata == null) return null
        val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        val title = metadata.firstText(
            MediaMetadata.METADATA_KEY_TITLE,
            MediaMetadata.METADATA_KEY_DISPLAY_TITLE
        )
        val artist = metadata.firstText(
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
        )
        val album = metadata.firstText(MediaMetadata.METADATA_KEY_ALBUM)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        val track = TrackIdentity(
            id = id?.trim()?.takeIf(String::isNotEmpty),
            title = title?.trim()?.takeIf(String::isNotEmpty),
            artist = artist?.trim()?.takeIf(String::isNotEmpty),
            album = album?.trim()?.takeIf(String::isNotEmpty),
            durationMs = duration
        )
        return track.takeUnless { it.isBlank }
    }

    private fun MediaMetadata.firstText(vararg keys: String): String? {
        keys.forEach { key ->
            getString(key)?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
        }
        return null
    }
}
