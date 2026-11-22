/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.andrealtb.coloroslyrics.provider.core.publisher.ColorOSLyricJsonEncoder
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import java.util.Locale

/**
 * Builds the `lyricInfo` value this Provider writes, on top of whatever the host already published.
 *
 * Mode selection follows the integration guide:
 * - host payload with a timed `lyric` and its own word lane -> append only (keep every official
 *   field, add the optional TTML lane);
 * - host payload without a word lane and this Provider has word timing -> re-encode, because a
 *   line-only official payload cannot express the renderer's word-level path;
 * - no host payload (the player's own ColorOS toggle is off) -> encode from scratch.
 */
object NeriPayloadComposer {

    data class Outcome(
        val json: String,
        val appendedToHostPayload: Boolean,
        val source: String
    )

    private val TIMED_TAG = Regex("""[\[<]\d{1,3}:\d{2}(?:[.:]\d{1,3})?[\]>]""")

    fun compose(
        track: TrackIdentity,
        lines: List<RichLyricLine>,
        ttml: String?,
        generation: Long,
        playerPackage: String,
        hostLyricInfo: String?,
        source: String
    ): Outcome? {
        val safeLines = lines.filter { !it.text.isNullOrBlank() }.sortedBy { it.begin }
        if (safeLines.isEmpty()) return null
        val safeTtml = ttml?.takeIf { it.isNotBlank() }

        if (hostUsable(hostLyricInfo)) {
            val host = hostLyricInfo!!
            val hostHasWords = host.contains("\"rawLyric\"")
            if (hostHasWords || !hasWordTiming(safeLines)) {
                val appended = appendTtml(host, safeTtml)
                if (appended != null && appended != host) {
                    return Outcome(appended, appendedToHostPayload = true, source = source)
                }
                return if (appended == host) Outcome(host, appendedToHostPayload = true, source = source) else null
            }
        }

        val encoded = ColorOSLyricJsonEncoder.encode(
            track = track,
            lines = safeLines,
            trackGeneration = generation,
            playerPackage = playerPackage,
            ttml = safeTtml
        ) ?: return null
        if (encoded.jsonValue.length > NeriPlayerConstants.MAX_PAYLOAD_CHARS) {
            // Drop the optional lane before dropping the lyric itself.
            val withoutTtml = ColorOSLyricJsonEncoder.encode(
                track = track,
                lines = safeLines,
                trackGeneration = generation,
                playerPackage = playerPackage,
                ttml = null
            ) ?: return null
            if (withoutTtml.jsonValue.length > NeriPlayerConstants.MAX_PAYLOAD_CHARS) return null
            return Outcome(withoutTtml.jsonValue, appendedToHostPayload = false, source = source)
        }
        return Outcome(encoded.jsonValue, appendedToHostPayload = false, source = source)
    }

    fun hostUsable(hostLyricInfo: String?): Boolean {
        if (hostLyricInfo.isNullOrBlank()) return false
        val trimmed = hostLyricInfo.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        if (!trimmed.contains("\"lyric\"")) return false
        return TIMED_TAG.containsMatchIn(trimmed)
    }

    /**
     * Adds `ttml` to an existing host payload by editing the JSON object in place, so every official
     * field (id, songId, lyricType, translation aliases, sessionGeneration, unknown keys) survives
     * byte for byte. Returns the input unchanged when the host already carries a TTML lane or the
     * addition would exceed the payload budget.
     */
    fun appendTtml(hostJson: String, ttml: String?): String? {
        if (ttml.isNullOrBlank()) return hostJson
        if (hostJson.contains("\"ttml\"")) return hostJson
        val trimmed = hostJson.trimEnd()
        if (!trimmed.endsWith("}")) return null
        val body = trimmed.dropLast(1).trimEnd()
        val addition = buildString(ttml.length + 24) {
            if (body.length > 1) append(',')
            append("\"ttml\":\"")
            append(escapeJson(ttml))
            append('"')
        }
        val candidate = body + addition + "}"
        if (candidate.length > NeriPlayerConstants.MAX_PAYLOAD_CHARS) return hostJson
        return candidate
    }

    fun hasWordTiming(lines: List<RichLyricLine>): Boolean =
        lines.any { !it.words.isNullOrEmpty() }

    fun formatLrcTime(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        return String.format(
            Locale.ROOT,
            "[%02d:%02d.%03d]",
            safe / 60_000L,
            safe / 1_000L % 60L,
            safe % 1_000L
        )
    }

    internal fun escapeJson(value: String): String = buildString(value.length + 16) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
}
