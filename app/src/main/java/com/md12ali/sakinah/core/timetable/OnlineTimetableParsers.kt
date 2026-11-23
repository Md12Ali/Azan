package com.md12ali.sakinah.core.timetable

import com.md12ali.sakinah.core.prayer.Prayer
import java.time.LocalDate
import java.time.LocalTime

/**
 * Pure-Kotlin parsers for mosque timetable pages (no Android types, so they are unit-testable).
 * The Android TimetableFetcher downloads the HTML and hands it to these.
 *
 * IMPORTANT: these platforms have no official public API. The parsers read what the public page
 * contains and fail safely (return empty) if a site changes – the app then moves to the next source
 * and finally to on-device calculation, so the Adhan never silently stops.
 */
object OnlineTimetableParsers {

    private val five = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
    private val six = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

    // ------------------------------------------------------------------ Mawaqit

    /**
     * Mawaqit mosque pages embed `var confData = {...};` containing:
     *  - "calendar": 12 month objects, each { "1": ["05:30","07:10","12:50","15:10","17:20","18:50"], ... }
     *    (fajr, shuruq, dhuhr, asr, maghrib, isha)
     *  - "iqamaCalendar": same shape with 5 entries per day, either "+10" (minutes after begin) or "HH:mm".
     * Returns every day of [year] that could be read.
     */
    fun parseMawaqit(html: String, year: Int): List<TimetableRow> {
        val start = html.indexOf("confData")
        if (start < 0) return emptyList()
        val braceStart = html.indexOf('{', start)
        if (braceStart < 0) return emptyList()
        val root = runCatching { MiniJson(html, braceStart).parseValue() }.getOrNull() as? Map<*, *> ?: return emptyList()
        val calendar = root["calendar"] as? List<*> ?: return emptyList()
        val iqama = root["iqamaCalendar"] as? List<*>

        val rows = mutableListOf<TimetableRow>()
        calendar.forEachIndexed { monthIdx, monthObj ->
            val month = monthObj as? Map<*, *> ?: return@forEachIndexed
            val iqMonth = iqama?.getOrNull(monthIdx) as? Map<*, *>
            month.forEach { (dayKey, value) ->
                val day = dayKey.toString().toIntOrNull() ?: return@forEach
                val date = runCatching { LocalDate.of(year, monthIdx + 1, day) }.getOrNull() ?: return@forEach
                val times = (value as? List<*>)?.map { it.toString() } ?: return@forEach
                if (times.size < 6) return@forEach
                val begins = six.zip(times).mapNotNull { (p, t) -> TimetableCsv.parseTime(t, p)?.let { p to it } }.toMap()
                val jamaat = mutableMapOf<Prayer, LocalTime>()
                (iqMonth?.get(dayKey) as? List<*>)?.map { it.toString() }?.let { iq ->
                    five.zip(iq).forEach { (p, v) ->
                        val begin = begins[p] ?: return@forEach
                        val t = if (v.trim().startsWith("+")) v.trim().drop(1).toLongOrNull()?.let { begin.plusMinutes(it) }
                                else TimetableCsv.parseTime(v, p)
                        if (t != null) jamaat[p] = t
                    }
                }
                if (begins.keys.containsAll(five)) rows += TimetableRow(date, begins, jamaat)
            }
        }
        return rows.sortedBy { it.date }
    }

    // ---------------------------------------------------------------- Masjidbox

    /**
     * Masjidbox public pages show, per prayer, an "Athan" (begin) time and an "Iqamah" (jamaat) time,
     * plus a single Shuruq time. We strip the HTML to text and read the first two times after each
     * prayer name. Returns today's row only (the fetcher runs daily, so the cache fills up over time).
     */
    fun parseMasjidbox(html: String, today: LocalDate): TimetableRow? {
        val text = htmlToText(html)
        val begins = mutableMapOf<Prayer, LocalTime>()
        val jamaat = mutableMapOf<Prayer, LocalTime>()
        val names = mapOf(
            Prayer.FAJR to "Fajr", Prayer.DHUHR to "(?:Dhuhr|Zuhr|Duhr)", Prayer.ASR to "Asr",
            Prayer.MAGHRIB to "Maghrib", Prayer.ISHA to "Isha"
        )
        for ((p, n) in names) {
            // Prefer a match that has both an Athan and an Iqamah time (skips countdown text like "5h to Fajr").
            val all = Regex("""\b$n\b\D{0,80}?(\d{1,2}[:.]\d{2})(?:\D{0,80}?(\d{1,2}[:.]\d{2}))?""", RegexOption.IGNORE_CASE)
                .findAll(text).toList()
            val m = all.firstOrNull { it.groupValues[2].isNotBlank() } ?: all.firstOrNull() ?: return null
            val a = TimetableCsv.parseTime(m.groupValues[1], p) ?: return null
            val b = m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { TimetableCsv.parseTime(it, p) }
            // Begin is always the earlier of the two.
            if (b != null && b.isBefore(a)) { begins[p] = b; jamaat[p] = a } else { begins[p] = a; b?.let { jamaat[p] = it } }
        }
        Regex("""\b(?:Shuruq|Sunrise|Shurooq)\b\D{0,40}?(\d{1,2}[:.]\d{2})""", RegexOption.IGNORE_CASE).find(text)
            ?.let { TimetableCsv.parseTime(it.groupValues[1], Prayer.SUNRISE) }?.let { begins[Prayer.SUNRISE] = it }
        return TimetableRow(today, begins, jamaat)
    }

    // ------------------------------------------------------------ website discovery

    /** Looks for timetable links on a mosque's own website. */
    fun discoverLinks(html: String): List<String> {
        val out = mutableListOf<String>()
        Regex("""https?://(?:www\.)?masjidbox\.com/prayer-times/([a-z0-9-]+)""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { out += "masjidbox:" + it.groupValues[1].lowercase() }
        Regex("""https?://(?:www\.)?mawaqit\.net/(?:[a-z]{2}/)?(?:w/)?([a-z0-9-]+)""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.groupValues[1].lowercase() }
            .filter { it !in setOf("en", "fr", "ar", "api", "static", "w") }
            .forEach { out += "mawaqit:$it" }
        Regex("""https?://[^\s"'<>]+\.csv""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { out += "csv:" + it.value }
        Regex("""https?://docs\.google\.com/spreadsheets/[^\s"'<>]+output=csv""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { out += "csv:" + it.value }
        return out.distinct()
    }

    fun htmlToText(html: String): String = html
        .replace(Regex("(?is)<script.*?</script>"), " ")
        .replace(Regex("(?is)<style.*?</style>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
}

/** Tiny tolerant JSON reader (objects, arrays, strings, numbers, true/false/null) – avoids extra dependencies. */
internal class MiniJson(private val s: String, private var i: Int) {
    fun parseValue(): Any? {
        skipWs()
        return when (s[i]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> { i += 4; true }
            'f' -> { i += 5; false }
            'n' -> { i += 4; null }
            else -> parseNumber()
        }
    }

    private fun parseObject(): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        i++; skipWs()
        if (s[i] == '}') { i++; return map }
        while (true) {
            skipWs(); val key = parseString(); skipWs()
            i++ // ':'
            map[key] = parseValue(); skipWs()
            if (s[i] == ',') { i++; continue }
            i++; return map // '}'
        }
    }

    private fun parseArray(): List<Any?> {
        val list = ArrayList<Any?>()
        i++; skipWs()
        if (s[i] == ']') { i++; return list }
        while (true) {
            list += parseValue(); skipWs()
            if (s[i] == ',') { i++; continue }
            i++; return list
        }
    }

    private fun parseString(): String {
        val sb = StringBuilder(); i++
        while (s[i] != '"') {
            if (s[i] == '\\') {
                i++
                when (s[i]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                    'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                    else -> sb.append(s[i])
                }
            } else sb.append(s[i])
            i++
        }
        i++
        return sb.toString()
    }

    private fun parseNumber(): Any {
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
        val str = s.substring(start, i)
        return str.toLongOrNull() ?: str.toDouble()
    }

    private fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
}
