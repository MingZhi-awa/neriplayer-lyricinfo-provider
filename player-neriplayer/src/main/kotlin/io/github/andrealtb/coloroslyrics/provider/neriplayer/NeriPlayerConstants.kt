/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

object NeriPlayerConstants {
    const val MODULE_PACKAGE = "io.github.andrealtb.coloroslyrics.provider.neriplayer"
    const val HOST_PACKAGE = "moe.ouom.neriplayer"
    const val HOST_DEBUG_PACKAGE = "moe.ouom.neriplayer.debug"

    /** lyricInfo field: the only key this Provider owns. */
    const val METADATA_KEY_LYRIC_INFO = "lyricInfo"

    val HOST_PACKAGES: Set<String> = setOf(HOST_PACKAGE, HOST_DEBUG_PACKAGE)

    /**
     * NeriPlayer's own AMLL DB client defaults to this mirror. Requests mirror what the player
     * would send, so a cached document matches the one the player would have parsed.
     */
    const val AMLL_BASE_URL = "https://amlldb.bikonoo.com"
    const val AMLL_USER_AGENT = "NeriPlayer/1.0 (https://github.com/cwuom/NeriPlayer)"

    /** Line-only fallback when AMLL has no word-timed document for the track. */
    const val LRCLIB_BASE_URL = "https://lrclib.net"
    const val LRCLIB_USER_AGENT = "ColorOS-Live-Lyrics-Provider-NeriPlayer/1.0.0"

    /** Kept well under the shared metadata parcel budget so artwork survives. */
    const val MAX_PAYLOAD_CHARS = 120_000
    const val MAX_SEARCH_CANDIDATES = 5
    const val MAX_CACHED_TRACKS = 16
    const val NETWORK_TIMEOUT_MS = 8_000

    /** Duration tolerance for matching a lyric document to the playing track. */
    const val DURATION_TOLERANCE_MS = 3_000L
}
