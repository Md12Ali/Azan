package com.md12ali.sakinah.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP GET (no OkHttp/Retrofit needed for a handful of requests). */
object Http {
    suspend fun get(url: String, timeoutMs: Int = 15_000): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AzanApp/1.0")
                setRequestProperty("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
                setRequestProperty("Accept-Language", "en-GB,en")
            }
            try {
                if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** Form POST (used for the OpenStreetMap Overpass API). */
    suspend fun postForm(url: String, field: String, value: String, timeoutMs: Int = 25_000): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = field + "=" + java.net.URLEncoder.encode(value, "UTF-8")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = timeoutMs; readTimeout = timeoutMs
                setRequestProperty("User-Agent", "AzanApp/1.0 (Android prayer-times app; github.com/Md12Ali/Azan)")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray()) }
                if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
            } finally { conn.disconnect() }
        }.getOrNull()
    }

    /** Follows redirects (e.g. maps.app.goo.gl short links) and returns every URL visited, last = final. */
    suspend fun redirectChain(url: String, maxHops: Int = 8): List<String> = withContext(Dispatchers.IO) {
        val chain = mutableListOf(url)
        var current = url
        repeat(maxHops) {
            val next = runCatching {
                val c = (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false; connectTimeout = 10_000; readTimeout = 10_000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
                }
                try {
                    if (c.responseCode in 300..399) c.getHeaderField("Location")?.let { URL(URL(current), it).toString() } else null
                } finally { c.disconnect() }
            }.getOrNull() ?: return@withContext chain
            chain += next; current = next
        }
        chain
    }
}
