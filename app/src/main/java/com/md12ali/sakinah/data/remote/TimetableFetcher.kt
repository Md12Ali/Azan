package com.md12ali.sakinah.data.remote

import android.content.Context
import com.md12ali.sakinah.core.location.MosqueProfile
import com.md12ali.sakinah.core.location.TimetableSource
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.core.timetable.OnlineTimetableParsers
import com.md12ali.sakinah.core.timetable.TimetableCsv
import com.md12ali.sakinah.core.timetable.TimetableRow
import com.md12ali.sakinah.data.local.TimetableDao
import com.md12ali.sakinah.data.local.TimetableDayEntity
import java.time.LocalDate

/**
 * Finds and downloads a mosque's OWN published timetable, then caches it in Room so the Adhan keeps
 * working offline.
 *
 * Order tried:
 *  1. the source that worked last time / the one the user attached ("masjidbox:…", "mawaqit:…", "csv:…")
 *  2. the mosque's listed sources (Masjidbox, bundled CSV, website discovery, slug guesses)
 *
 * Returns the source string that worked, or null (the repository then falls back to calculation).
 */
class TimetableFetcher(
    private val context: Context,
    private val dao: TimetableDao
) {
    private var hanafiAsr = true

    suspend fun fetch(
        mosque: MosqueProfile, rememberedSource: String?, today: LocalDate = LocalDate.now(), hanafiAsr: Boolean = true
    ): String? {
        this.hanafiAsr = hanafiAsr
        val candidates = buildList {
            rememberedSource?.let { decode(it)?.let(::add) }
            addAll(mosque.sources)
        }.distinct()

        for (source in candidates) {
            val rows = runCatching { load(source, today) }.getOrNull().orEmpty()
            val usable = rows.filter { !it.date.isBefore(today.minusDays(1)) }
            if (usable.isNotEmpty()) {
                val key = encode(source)
                dao.upsert(usable.map { it.toEntity(mosque.id, key) })
                dao.prune(today.minusDays(7).toString())
                return key
            }
        }
        return null
    }

    private suspend fun load(source: TimetableSource, today: LocalDate): List<TimetableRow> = when (source) {
        is TimetableSource.Masjidbox -> {
            val html = Http.get("https://masjidbox.com/prayer-times/${source.slug}")
            html?.let { OnlineTimetableParsers.parseMasjidbox(it, today) }?.let { listOf(it) }.orEmpty()
        }
        is TimetableSource.Mawaqit -> {
            val html = Http.get("https://mawaqit.net/en/${source.slug}")
            html?.let { OnlineTimetableParsers.parseMawaqit(it, today.year) }.orEmpty()
        }
        is TimetableSource.CsvUrl -> Http.get(source.url)?.let { TimetableCsv.parse(it) }.orEmpty()
        is TimetableSource.BundledCsv -> runCatching {
            context.assets.open(source.assetPath).bufferedReader().use { TimetableCsv.parse(it.readText()) }
        }.getOrDefault(emptyList())
        is TimetableSource.HtmlTable -> Http.get(source.url)
            ?.let { OnlineTimetableParsers.parseDatedTable(it, source.layout, hanafiAsr) }.orEmpty()
        is TimetableSource.TodayWidget -> Http.get(source.url)
            ?.let { OnlineTimetableParsers.parseTodayWidget(it, today) }?.let { listOf(it) }.orEmpty()
        is TimetableSource.DiscoverFromWebsite -> {
            val html = Http.get(source.url).orEmpty()
            OnlineTimetableParsers.discoverLinks(html)
                .mapNotNull { decode(it) }
                .firstNotNullOfOrNull { found -> load(found, today).takeIf { it.isNotEmpty() } }
                // No link found? The home page may itself show today's times.
                ?: OnlineTimetableParsers.parseTodayWidget(html, today)?.let { listOf(it) }.orEmpty()
        }
    }

    private fun TimetableRow.toEntity(mosqueId: String, source: String) = TimetableDayEntity(
        mosqueId = mosqueId, date = date.toString(),
        fajr = begins.getValue(Prayer.FAJR).toString(), sunrise = begins[Prayer.SUNRISE]?.toString(),
        dhuhr = begins.getValue(Prayer.DHUHR).toString(), asr = begins.getValue(Prayer.ASR).toString(),
        maghrib = begins.getValue(Prayer.MAGHRIB).toString(), isha = begins.getValue(Prayer.ISHA).toString(),
        fajrJ = jamaat[Prayer.FAJR]?.toString(), dhuhrJ = jamaat[Prayer.DHUHR]?.toString(),
        asrJ = jamaat[Prayer.ASR]?.toString(), maghribJ = jamaat[Prayer.MAGHRIB]?.toString(),
        ishaJ = jamaat[Prayer.ISHA]?.toString(),
        source = source, fetchedAt = System.currentTimeMillis()
    )

    companion object {
        /** "masjidbox:slug" | "mawaqit:slug" | "csv:url" | a pasted full URL → TimetableSource. */
        fun decode(s: String): TimetableSource? {
            val t = s.trim()
            return when {
                t.startsWith("masjidbox:") -> TimetableSource.Masjidbox(t.removePrefix("masjidbox:"))
                t.startsWith("mawaqit:") -> TimetableSource.Mawaqit(t.removePrefix("mawaqit:"))
                t.startsWith("csv:") -> TimetableSource.CsvUrl(t.removePrefix("csv:"))
                t.startsWith("asset:") -> TimetableSource.BundledCsv(t.removePrefix("asset:"))
                t.startsWith("table:") -> t.removePrefix("table:").split('|', limit = 2)
                    .takeIf { it.size == 2 }?.let { TimetableSource.HtmlTable(it[1], it[0]) }
                t.startsWith("widget:") -> TimetableSource.TodayWidget(t.removePrefix("widget:"))
                t.startsWith("http") -> OnlineTimetableParsers.discoverLinks(t).firstOrNull()?.let { decode(it) }
                    ?: if (t.contains("csv", ignoreCase = true)) TimetableSource.CsvUrl(t)
                    else TimetableSource.DiscoverFromWebsite(t)
                else -> null
            }
        }

        fun encode(s: TimetableSource): String = when (s) {
            is TimetableSource.Masjidbox -> "masjidbox:${s.slug}"
            is TimetableSource.Mawaqit -> "mawaqit:${s.slug}"
            is TimetableSource.CsvUrl -> "csv:${s.url}"
            is TimetableSource.BundledCsv -> "asset:${s.assetPath}"
            is TimetableSource.HtmlTable -> "table:${s.layout}|${s.url}"
            is TimetableSource.TodayWidget -> "widget:${s.url}"
            is TimetableSource.DiscoverFromWebsite -> s.url
        }

        fun describe(source: String?): String = when {
            source == null -> "calculated"
            source.startsWith("masjidbox:") -> "Masjidbox"
            source.startsWith("mawaqit:") -> "Mawaqit"
            source.startsWith("csv:") -> "CSV link"
            source.startsWith("asset:") -> "built-in timetable"
            source.startsWith("table:") || source.startsWith("widget:") -> "mosque website"
            else -> "mosque website"
        }
    }
}
