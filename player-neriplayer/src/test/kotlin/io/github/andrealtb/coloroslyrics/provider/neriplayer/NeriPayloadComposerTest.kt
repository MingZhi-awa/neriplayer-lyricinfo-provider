/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import io.github.andrealtb.coloroslyrics.provider.core.model.TrackIdentity
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LyricWord
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NeriPayloadComposerTest {

    private val track = TrackIdentity(
        id = "track-42",
        title = "示例歌曲",
        artist = "示例歌手",
        album = "示例专辑",
        durationMs = 180_000L
    )

    private fun wordTimedLines(): List<RichLyricLine> = listOf(
        RichLyricLine(
            begin = 10_000L,
            end = 12_800L,
            text = "第一句",
            words = listOf(
                LyricWord(begin = 10_000L, end = 10_700L, text = "第一"),
                LyricWord(begin = 10_700L, end = 12_800L, text = "句")
            ),
            secondary = "First line"
        ),
        RichLyricLine(begin = 14_500L, end = 17_000L, text = "第二句")
    )

    @Test
    fun `host payload without word lane is replaced by a word-timed payload`() {
        val host = """{"songName":"示例歌曲","lyric":"[00:10.000]第一句\n[00:14.500]第二句\n","noLyric":false}"""
        val outcome = NeriPayloadComposer.compose(
            track = track,
            lines = wordTimedLines(),
            ttml = null,
            generation = 3L,
            playerPackage = NeriPlayerConstants.HOST_PACKAGE,
            hostLyricInfo = host,
            source = "amll"
        )
        assertNotNull(outcome)
        assertFalse(outcome.appendedToHostPayload)
        assertTrue(outcome.json.contains("\"rawLyric\""))
        assertTrue(outcome.json.contains("\"translationLyric\""))
        assertTrue(outcome.json.contains("\"sessionGeneration\":3"))
    }

    @Test
    fun `host payload with its own word lane keeps every official field and gains ttml`() {
        val host = """{"id":"official","songId":"s-1","lyricType":0,"lyric":"[00:10.000]第一句\n","rawLyric":"[00:10.000]<00:10.000>第一<00:10.700>句<00:12.800>\n","noLyric":false}"""
        val outcome = NeriPayloadComposer.compose(
            track = track,
            lines = wordTimedLines(),
            ttml = "<tt xmlns=\"http://www.w3.org/ns/ttml\"></tt>",
            generation = 4L,
            playerPackage = NeriPlayerConstants.HOST_PACKAGE,
            hostLyricInfo = host,
            source = "amll"
        )
        assertNotNull(outcome)
        assertTrue(outcome.appendedToHostPayload)
        assertTrue(outcome.json.contains("\"id\":\"official\""))
        assertTrue(outcome.json.contains("\"songId\":\"s-1\""))
        assertTrue(outcome.json.contains("\"ttml\""))
    }

    @Test
    fun `append keeps the host payload untouched when ttml already exists`() {
        val host = """{"lyric":"[00:10.000]x","ttml":"<tt/>"}"""
        assertEquals(host, NeriPayloadComposer.appendTtml(host, "<tt>new</tt>"))
    }

    @Test
    fun `append escapes quotes and newlines`() {
        val host = """{"lyric":"[00:10.000]x"}"""
        val appended = NeriPayloadComposer.appendTtml(host, "<tt a=\"b\">\n</tt>")
        assertNotNull(appended)
        assertTrue(appended.contains("\\\""))
        assertTrue(appended.contains("\\n"))
        assertFalse(appended.contains("\n"))
    }

    @Test
    fun `payload is dropped when it exceeds the budget`() {
        val huge = "x".repeat(NeriPlayerConstants.MAX_PAYLOAD_CHARS)
        val outcome = NeriPayloadComposer.compose(
            track = track,
            lines = listOf(RichLyricLine(begin = 0L, end = 1_000L, text = huge)),
            ttml = null,
            generation = 1L,
            playerPackage = NeriPlayerConstants.HOST_PACKAGE,
            hostLyricInfo = null,
            source = "lrclib"
        )
        assertNull(outcome)
    }

    @Test
    fun `untimed host json is not treated as usable`() {
        assertFalse(NeriPayloadComposer.hostUsable("""{"lyric":"no timing here"}"""))
        assertFalse(NeriPayloadComposer.hostUsable(""))
        assertFalse(NeriPayloadComposer.hostUsable(null))
        assertTrue(NeriPayloadComposer.hostUsable("""{"lyric":"[00:10.000]x"}"""))
    }
}
