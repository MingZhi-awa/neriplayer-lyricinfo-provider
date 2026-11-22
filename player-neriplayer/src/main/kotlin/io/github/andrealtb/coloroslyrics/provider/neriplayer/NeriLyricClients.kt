/*
 * Copyright 2026 猫羽bot
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.andrealtb.coloroslyrics.provider.neriplayer

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal, dependency-free client for the AMLL DB mirror NeriPlayer itself defaults to.
 *
 * Only the two endpoints the player uses are implemented: `api/search-lyrics` and
 * `raw-lyrics/<file>.ttml`. The request body shape and the `file` field validation mirror
 * NeriPlayer's own client so a returned document is the same one the player would have parsed.
 */
internal object NeriAmllClient {

    data class SearchResult(val file: String, val title: String, val artists: List<String>, val score: Int)

    fun search(trackName: String, artistName: String): List<SearchResult> {
        val query = listOf(trackName, artistName).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (query.isEmpty()) return emptyList()
        val body = JSONObject()
            .put("query", query)
            .put("type", "title")
            .toString()
        val response = request(
            url = "${NeriPlayerConstants.AMLL_BASE_URL}/api/search-lyrics",
            method = "POST",
            contentType = "application/json; charset=utf-8",
            body = body.toByteArray(Charsets.UTF_8)
        ) ?: return emptyList()
        return runCatching {
            val array = JSONArray(response)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val file = item.optString("file").trim()
                    if (!file.endsWith(".ttml") || file.contains('/')) continue
                    add(
                        SearchResult(
                            file = file,
                            title = item.optString("title").trim(),
                            artists = item.stringList("artists"),
                            score = item.optInt("score")
                        )
                    )
                    if (size >= NeriPlayerConstants.MAX_SEARCH_CANDIDATES) break
                }
            }
        }.getOrDefault(emptyList())
    }

    fun fetchDocument(file: String): String? {
        val safe = file.trim().takeIf { it.endsWith(".ttml") && !it.contains('/') } ?: return null
        return request(
            url = "${NeriPlayerConstants.AMLL_BASE_URL}/raw-lyrics/" + URLEncoder.encode(safe, "UTF-8"),
            method = "GET",
            contentType = null,
            body = null
        )
    }

    private fun JSONObject.stringList(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    private fun request(url: String, method: String, contentType: String?, body: ByteArray?): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = NeriPlayerConstants.NETWORK_TIMEOUT_MS
                readTimeout = NeriPlayerConstants.NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", NeriPlayerConstants.AMLL_USER_AGENT)
                setRequestProperty("Accept", "*/*")
                if (contentType != null) setRequestProperty("Content-Type", contentType)
                if (body != null) {
                    doOutput = true
                    setFixedLengthStreamingMode(body.size)
                    outputStream.use { it.write(body) }
                }
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { readAll(it) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }
}

/**
 * Line-only fallback used when AMLL has no document for the track. LRCLIB only produces a plain
 * timed LRC, so the resulting payload carries no word lane and the renderer stays line-level.
 */
internal object NeriLrclibClient {

    fun fetchSyncedLyrics(trackName: String, artistName: String, durationMs: Long): String? {
        if (trackName.isBlank()) return null
        val query = buildString {
            append("track_name=").append(encode(trackName))
            if (artistName.isNotBlank()) append("&artist_name=").append(encode(artistName))
            if (durationMs > 0) append("&duration=").append(durationMs / 1000L)
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("${NeriPlayerConstants.LRCLIB_BASE_URL}/api/get?$query").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = NeriPlayerConstants.NETWORK_TIMEOUT_MS
                readTimeout = NeriPlayerConstants.NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", NeriPlayerConstants.LRCLIB_USER_AGENT)
            }
            if (connection.responseCode !in 200..299) return null
            val text = connection.inputStream.use { readAll(it) }
            val lyrics = runCatching { JSONObject(text).optString("syncedLyrics") }.getOrNull()
            lyrics?.takeIf { it.contains('[') && it.length > 16 }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

private fun readAll(input: java.io.InputStream): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
        if (output.size() > 4 * 1024 * 1024) break
    }
    return output.toString(Charsets.UTF_8.name())
}
