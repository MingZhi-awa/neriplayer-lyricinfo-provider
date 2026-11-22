/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.LyricWord
import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NeriTtmlTranslationsTest {

    private val ttml = """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
          <body>
            <div>
              <p begin="00:10.000" end="00:13.000">
                <span begin="00:10.000" end="00:10.700">第一</span><span begin="00:10.700" end="00:13.000">句</span>
                <span begin="00:10.000" end="00:13.000" ttm:role="x-translation">First line</span>
              </p>
              <p begin="00:14.500" end="00:17.000">
                <span begin="00:14.500" end="00:17.000">第二句</span>
                <span begin="00:14.500" end="00:17.000" ttm:role="x-roman">dai ni ju</span>
              </p>
            </div>
          </body>
        </tt>
    """.trimIndent()

    @Test
    fun `translation span is extracted and romanization is ignored`() {
        val translations = NeriTtmlTranslations.extract(ttml)
        assertEquals(1, translations.size)
        assertEquals(10_000L, translations.single().beginMs)
        assertEquals("First line", translations.single().text)
    }

    @Test
    fun `translation is paired to exactly one line`() {
        val lines = listOf(
            RichLyricLine(
                begin = 10_000L,
                end = 13_000L,
                text = "第一句",
                words = listOf(LyricWord(begin = 10_000L, end = 10_700L, text = "第一"))
            ),
            RichLyricLine(begin = 14_500L, end = 17_000L, text = "第二句")
        )
        val paired = NeriTtmlTranslations.apply(lines, NeriTtmlTranslations.extract(ttml))
        assertEquals("First line", paired[0].secondary)
        assertNull(paired[1].secondary)
    }

    @Test
    fun `translation never overwrites an identical primary line`() {
        val lines = listOf(RichLyricLine(begin = 10_000L, end = 13_000L, text = "First line"))
        val paired = NeriTtmlTranslations.apply(
            lines,
            listOf(NeriTtmlTranslations.TranslationLine(10_000L, "First line"))
        )
        assertNull(paired.single().secondary)
    }

    @Test
    fun `unparsable document yields no translations`() {
        assertTrue(NeriTtmlTranslations.extract("not xml").isEmpty())
        assertTrue(NeriTtmlTranslations.extract(null).isEmpty())
    }
}
