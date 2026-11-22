/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import io.github.andrealtb.coloroslyrics.provider.parser.lrc.model.RichLyricLine
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * AMLL TTML keeps translations in a sibling `span` whose `ttm:role` is `x-translation`, and the
 * shared [io.github.andrealtb.coloroslyrics.provider.parser.ttml.TtmlParser] deliberately skips
 * that span so it never leaks into the primary lane. This extractor reads only the translation
 * spans of each `<p>` and pairs them with the `<p>` start time, which is exactly the line start
 * the parser produced. Romanization (`x-roman`) and background vocals (`x-bg`) are never used.
 */
object NeriTtmlTranslations {

    private const val MATCH_TOLERANCE_MS = 60L

    data class TranslationLine(val beginMs: Long, val text: String)

    fun extract(ttml: String?): List<TranslationLine> {
        if (ttml.isNullOrBlank()) return emptyList()
        val doc = runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
            try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
            try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
            try { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) } catch (_: Exception) {}
            try { factory.isXIncludeAware = false } catch (_: Exception) {}
            factory.isExpandEntityReferences = false
            factory.newDocumentBuilder().parse(ttml.byteInputStream())
        }.getOrNull() ?: return emptyList()

        val globalOffsetMs = runCatching { readGlobalOffsetMs(doc.documentElement) }.getOrDefault(0L)
        val paragraphs = doc.getElementsByTagNameNS("*", "p")
        val result = mutableListOf<TranslationLine>()
        for (index in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(index) as? Element ?: continue
            val begin = attribute(paragraph, "begin").takeIf(String::isNotEmpty)
                ?: firstSpanBegin(paragraph)
                ?: continue
            val lineMs = (parseTimeMs(begin) - globalOffsetMs).coerceAtLeast(0L)
            val text = collectTranslationText(paragraph)
            if (text.isNotBlank()) result.add(TranslationLine(lineMs, text))
        }
        return result
    }

    /**
     * Pairs each translation with at most one primary line. Entries whose start does not land on a
     * primary line are dropped instead of being guessed onto a neighbour.
     */
    fun apply(lines: List<RichLyricLine>, translations: List<TranslationLine>): List<RichLyricLine> {
        if (translations.isEmpty() || lines.isEmpty()) return lines
        val consumed = BooleanArray(translations.size)
        return lines.map { line ->
            val match = translations.indexOfFirst { !consumed[it] && kotlin.math.abs(it.beginMs - line.begin) <= MATCH_TOLERANCE_MS }
            if (match < 0) {
                line
            } else {
                consumed[match] = true
                val text = translations[match].text
                if (text.isBlank() || text.trim() == line.text?.trim()) line else line.copy(secondary = text)
            }
        }
    }

    private fun collectTranslationText(paragraph: Element): String {
        val builder = StringBuilder()
        val nodes = paragraph.getElementsByTagNameNS("*", "span")
        for (index in 0 until nodes.length) {
            val span = nodes.item(index) as? Element ?: continue
            if (roleOf(span) != ROLE_TRANSLATION) continue
            val text = span.textContent.orEmpty().trim()
            if (text.isNotEmpty()) {
                if (builder.isNotEmpty()) builder.append(' ')
                builder.append(text)
            }
        }
        if (builder.isEmpty()) {
            // Some writers put the role on a nested node instead of the span itself.
            walk(paragraph) { element ->
                if (builder.isEmpty() && roleOf(element) == ROLE_TRANSLATION) {
                    builder.append(element.textContent.orEmpty().trim())
                }
            }
        }
        return builder.toString().replace('\n', ' ').trim()
    }

    private fun walk(root: Element, visit: (Element) -> Unit) {
        var child: Node? = root.firstChild
        while (child != null) {
            if (child is Element) {
                visit(child)
                walk(child, visit)
            }
            child = child.nextSibling
        }
    }

    private fun roleOf(element: Element): String =
        element.getAttributeNS("http://www.w3.org/ns/ttml#metadata", "role")
            .ifEmpty { element.getAttribute("ttm:role") }
            .ifEmpty { element.getAttribute("role") }

    private fun firstSpanBegin(paragraph: Element): String? {
        val spans = paragraph.getElementsByTagNameNS("*", "span")
        for (index in 0 until spans.length) {
            val span = spans.item(index) as? Element ?: continue
            if (roleOf(span).isNotEmpty()) continue
            val begin = attribute(span, "begin")
            if (begin.isNotEmpty()) return begin
        }
        return null
    }

    private fun readGlobalOffsetMs(root: Element?): Long {
        if (root == null) return 0L
        val offset = attribute(root, "lyricOffset")
        return if (offset.isEmpty()) 0L else parseTimeMs(offset)
    }

    private fun attribute(element: Element, name: String): String =
        element.getAttribute(name).ifEmpty {
            if (element.hasAttribute("tts:$name")) element.getAttribute("tts:$name") else ""
        }.trim()

    /**
     * Mirrors the shared TTML parser's clock rules exactly: `ms`/`h`/`m` suffixes for quantities,
     * `hh:mm:ss` for three segments and `mm:ss` for two. If these disagree with the parser, every
     * translation line would be paired against the wrong primary line.
     */
    private fun parseTimeMs(raw: String): Long {
        val value = raw.trim().replace(',', '.')
        if (value.isEmpty()) return 0L
        if (value.endsWith("ms", ignoreCase = true)) {
            return value.dropLast(2).toDoubleOrNull()?.toLong() ?: 0L
        }
        if (!value.contains(':')) {
            val multiplier = when {
                value.endsWith("h", ignoreCase = true) -> 3_600_000.0
                value.endsWith("m", ignoreCase = true) -> 60_000.0
                else -> 1_000.0
            }
            val numeric = if (value.lastOrNull()?.isLetter() == true) value.dropLast(1) else value
            return ((numeric.toDoubleOrNull() ?: 0.0) * multiplier).toLong()
        }
        val parts = value.split(':')
        return runCatching {
            when (parts.size) {
                3 -> parts[0].toLong() * 3_600_000L +
                    parts[1].toLong() * 60_000L +
                    (parts[2].toDouble() * 1_000.0).toLong()

                2 -> parts[0].toLong() * 60_000L + (parts[1].toDouble() * 1_000.0).toLong()
                1 -> (parts[0].toDouble() * 1_000.0).toLong()
                else -> 0L
            }
        }.getOrDefault(0L)
    }

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

    private const val ROLE_TRANSLATION = "x-translation"
}
