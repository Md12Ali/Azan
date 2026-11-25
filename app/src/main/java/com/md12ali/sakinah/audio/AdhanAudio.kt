package com.md12ali.sakinah.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real Adhan recordings, freely licensed on Wikimedia Commons.
 * They are downloaded once (≈1–3 MB each) into app storage and then play fully offline forever.
 * Credits are shown in the app (Adhan tab → About the recordings) as the licences require.
 */
object AdhanAudio {

    data class Recording(
        val id: String,
        val title: String,
        val credit: String,
        val license: String,
        val urls: List<String>,
        val ext: String
    )

    val catalog = listOf(
        Recording(
            "aaqib_azeez", "Adhan – Aaqib Azeez (clear, Sunnah style)",
            "Aaqib Azeez, recorded by Atcovi – Wikimedia Commons", "CC BY-SA 4.0",
            listOf(
                "https://upload.wikimedia.org/wikipedia/commons/7/7d/The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3",
                "https://commons.wikimedia.org/wiki/Special:FilePath/The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3"
            ), "mp3"
        ),
        Recording(
            "beautiful", "Beautiful Adhan (melodic)",
            "Adam-synagda – Wikimedia Commons", "CC0 (public domain)",
            listOf(
                "https://upload.wikimedia.org/wikipedia/commons/b/b0/Beautiful_adhan.ogg",
                "https://commons.wikimedia.org/wiki/Special:FilePath/Beautiful_adhan.ogg"
            ), "ogg"
        ),
        Recording(
            "short", "Short Adhan",
            "Wikimedia Commons (Adhan.ogg)", "CC0 (public domain)",
            listOf(
                "https://upload.wikimedia.org/wikipedia/commons/e/e7/Adhan.ogg",
                "https://commons.wikimedia.org/wiki/Special:FilePath/Adhan.ogg"
            ), "ogg"
        )
    )

    private fun dir(context: Context) = File(context.filesDir, "adhan_builtin").apply { mkdirs() }

    fun fileFor(context: Context, r: Recording) = File(dir(context), "${r.id}.${r.ext}")

    fun downloaded(context: Context): List<Pair<Recording, File>> =
        catalog.map { it to fileFor(context, it) }.filter { it.second.exists() && it.second.length() > 20_000 }

    /** Downloads any missing recordings. Safe to call often; returns how many are available. */
    suspend fun ensureDownloaded(context: Context): Int = withContext(Dispatchers.IO) {
        for (r in catalog) {
            val f = fileFor(context, r)
            if (f.exists() && f.length() > 20_000) continue
            for (u in r.urls) {
                if (download(u, f)) break
            }
        }
        downloaded(context).size
    }

    private fun download(url: String, out: File): Boolean {
        var current = url
        for (hop in 0 until 5) { // follow redirects manually (Special:FilePath → upload.wikimedia.org)
            val c = try {
                (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20_000; readTimeout = 60_000
                    // Wikimedia asks for a descriptive User-Agent.
                    setRequestProperty("User-Agent", "AzanApp/1.0 (Android prayer-times app; github.com/Md12Ali/Azan)")
                }
            } catch (e: Exception) { return false }
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    current = URL(URL(current), c.getHeaderField("Location") ?: return false).toString()
                    continue
                }
                if (code !in 200..299) return false
                val tmp = File(out.parentFile, out.name + ".part")
                c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                return tmp.length() > 20_000 && tmp.renameTo(out)
            } catch (e: Exception) {
                return false
            } finally {
                c.disconnect()
            }
        }
        return false
    }

    /**
     * Turns the user's setting into something playable, best first:
     * chosen file → bundled asset → downloaded voice → built-in Azan (res/raw/azan.mp3).
     * "" (the default) = built-in Azan.
     */
    const val BUILT_IN = "raw:azan"

    fun resolve(context: Context, setting: String, fajr: Boolean): String {
        if (setting.isBlank() || setting == BUILT_IN) return BUILT_IN
        if (setting.startsWith("/") && File(setting).exists()) return setting
        if (setting.isNotBlank() && !setting.startsWith("/") &&
            runCatching { context.assets.openFd(setting).close(); true }.getOrDefault(false)) return "asset:$setting"
        return BUILT_IN
    }
}
