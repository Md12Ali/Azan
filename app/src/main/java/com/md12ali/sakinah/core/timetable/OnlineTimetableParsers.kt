package com.md12ali.sakinah.core.timetable

import com.md12ali.sakinah.core.prayer.Prayer
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure-Kotlin parsers for mosque timetable pages (no Android types, so they are unit-testable).
 * The Android TimetableFetcher downloads the HTML and hands it to these.
 *
 * Mosques have no common API, so we support the page shapes UK central mosques actually use:
 *  1. Dated table (whole month/year)  – East London Mosque, Birmingham Central Mosque  → [parseDatedTable]
 *  2. "Today" box: name + begin + jamaat – Leicester, Manchester, Birmingham home, Masjidbox → [parseTodayWidget]
 *  3. Mawaqit embedded JSON                                                             → [parseMawaqit]
 * Everything fails safely (empty/null) so the app falls back to the next source and finally to calculation.
 */
object OnlineTimetableParsers {

    private val five = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
    private val six = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

    // ------------------------------------------------------------- dated tables

    /** What each time column in a dated row means. SKIP = ignore (e.g. Zawal, or the Asr Mithl you don't follow). */
    enum class Col { SUNRISE, FAJR, FAJR_J, DHUHR, DHUHR_J, ASR, ASR_J, MAGHRIB, MAGHRIB_J, ISHA, ISHA_J, ASR_MITHL1, ASR_MITHL2, SKIP }

    /** Known table layouts (time columns only, in order – non-time cells such as Hijri day/month are ignored). */
    val layouts: Map<String, List<Col>> = mapOf(
        // eastlondonmosque.org.uk/prayer-times – Sunrise | Fajr B J | Zuhr B J | Asr Mithl1 Mithl2 J | Maghrib B J | Isha B J
        "elm" to listOf(Col.SUNRISE, Col.FAJR, Col.FAJR_J, Col.DHUHR, Col.DHUHR_J, Col.ASR_MITHL1, Col.ASR_MITHL2, Col.ASR_J,
            Col.MAGHRIB, Col.MAGHRIB_J, Col.ISHA, Col.ISHA_J),
        // centralmosque.org.uk/timetable – Fajr B J | Sunrise | Zawal | Zuhr B J | Asr B J | Maghrib B J | Isha B J
        "bcm" to listOf(Col.FAJR, Col.FAJR_J, Col.SUNRISE, Col.SKIP, Col.DHUHR, Col.DHUHR_J, Col.ASR, Col.ASR_J,
            Col.MAGHRIB, Col.MAGHRIB_J, Col.ISHA, Col.ISHA_J)
    )

    private val timeRx = Regex("""\b(\d{1,2}[:.]\d{2})\s*([AaPp][Mm])?\b""")
    private val numericDateRx = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{4})\b""")
    private val longDateRx = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(January|February|March|April|May|June|July|August|September|October|November|December)\s+(\d{4})\b""", RegexOption.IGNORE_CASE)

    /**
     * Reads every row that starts with a date ("23/11/2026" or "23rd November 2026") followed by times.
     * @param hanafiAsr when the table lists both Asr Mithl 1 and Mithl 2, pick Mithl 2 (Hanafi) or Mithl 1.
     */
    fun parseDatedTable(html: String, layoutId: String, hanafiAsr: Boolean = true): List<TimetableRow> {
        val layout = layouts[layoutId] ?: return emptyList()
        val text = htmlToText(html)
        data class D(val date: LocalDate, val start: Int, val end: Int)
        val dates = buildList {
            numericDateRx.findAll(text).forEach { m ->
                runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }
                    .getOrNull()?.let { add(D(it, m.range.first, m.range.last + 1)) }
            }
            longDateRx.findAll(text).forEach { m ->
                runCatching {
                    LocalDate.parse("${m.groupValues[1]} ${m.groupValues[2].lowercase().replaceFirstChar { it.uppercase() }} ${m.groupValues[3]}",
                        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
                }.getOrNull()?.let { add(D(it, m.range.first, m.range.last + 1)) }
            }
        }.sortedBy { it.start }

        val rows = mutableMapOf<LocalDate, TimetableRow>()
        dates.forEachIndexed { i, d ->
            val segment = text.substring(d.end, if (i + 1 < dates.size) dates[i + 1].start else minOf(text.length, d.end + 400))
            val times = timeRx.findAll(segment).map { it.groupValues[1] + it.groupValues[2] }.toList()
            if (times.size < layout.size) return@forEachIndexed
            val begins = mutableMapOf<Prayer, LocalTime>()
            val jamaat = mutableMapOf<Prayer, LocalTime>()
            layout.zip(times).forEach { (col, raw) ->
                when (col) {
                    Col.SUNRISE -> TimetableCsv.parseTime(raw, Prayer.SUNRISE)?.let { begins[Prayer.SUNRISE] = it }
                    Col.FAJR -> TimetableCsv.parseTime(raw, Prayer.FAJR)?.let { begins[Prayer.FAJR] = it }
                    Col.FAJR_J -> TimetableCsv.parseTime(raw, Prayer.FAJR)?.let { jamaat[Prayer.FAJR] = it }
                    Col.DHUHR -> TimetableCsv.parseTime(raw, Prayer.DHUHR)?.let { begins[Prayer.DHUHR] = it }
                    Col.DHUHR_J -> TimetableCsv.parseTime(raw, Prayer.DHUHR)?.let { jamaat[Prayer.DHUHR] = it }
                    Col.ASR -> TimetableCsv.parseTime(raw, Prayer.ASR)?.let { begins[Prayer.ASR] = it }
                    Col.ASR_MITHL1 -> if (!hanafiAsr) TimetableCsv.parseTime(raw, Prayer.ASR)?.let { begins[Prayer.ASR] = it }
                    Col.ASR_MITHL2 -> if (hanafiAsr) TimetableCsv.parseTime(raw, Prayer.ASR)?.let { begins[Prayer.ASR] = it }
                    Col.ASR_J -> TimetableCsv.parseTime(raw, Prayer.ASR)?.let { jamaat[Prayer.ASR] = it }
                    Col.MAGHRIB -> TimetableCsv.parseTime(raw, Prayer.MAGHRIB)?.let { begins[Prayer.MAGHRIB] = it }
                    Col.MAGHRIB_J -> TimetableCsv.parseTime(raw, Prayer.MAGHRIB)?.let { jamaat[Prayer.MAGHRIB] = it }
                    Col.ISHA -> TimetableCsv.parseTime(raw, Prayer.ISHA)?.let { begins[Prayer.ISHA] = it }
                    Col.ISHA_J -> TimetableCsv.parseTime(raw, Prayer.ISHA)?.let { jamaat[Prayer.ISHA] = it }
                    Col.SKIP -> Unit
                }
            }
            if (begins.keys.containsAll(five) && isChronological(begins)) rows[d.date] = TimetableRow(d.date, begins, jamaat)
        }
        return rows.values.sortedBy { it.date }
    }

    // ------------------------------------------------------------ "today" boxes

    private val nameRx = Regex(
        """\b(Fajr|Fajar|Fajir|Subh|Sunrise|Shuruq|Shurooq|Sunrise|Dhuhr|Zuhr|Duhr|Zohar|Zuhar|Asr|Asar|Maghrib|Magrib|Maghreb|Isha|Ishaa|Esha|Ishā)\b""",
        RegexOption.IGNORE_CASE
    )

    private fun nameToPrayer(n: String): Prayer? = when (n.lowercase()) {
        "fajr", "fajar", "fajir", "subh" -> Prayer.FAJR
        "sunrise", "shuruq", "shurooq" -> Prayer.SUNRISE
        "dhuhr", "zuhr", "duhr", "zohar", "zuhar" -> Prayer.DHUHR
        "asr", "asar" -> Prayer.ASR
        "maghrib", "magrib", "maghreb" -> Prayer.MAGHRIB
        "isha", "ishaa", "esha", "ishā" -> Prayer.ISHA
        else -> null
    }

    /**
     * A "today" box where each prayer name is followed by its begin time and (optionally) jamaat time, e.g.
     *   "Fajar 6:00 7.00  Sunrise 7:36  Zuhr 11:54 1.15 …"          (Leicester Central Mosque)
     *   "Fajr Start: 5.43 Jamaat: 7.00 … Maghrib Jamaat: 4.04 …"     (Manchester Central Mosque)
     *   "Fajr 6:12 AM 6:30 AM Sunrise 7:41 AM Zuhr 11:58 AM 1:15 PM" (Birmingham Central Mosque)
     * Times are only taken from the text between one prayer name and the next, and the first block whose
     * five begin times are in order wins (skips menus and countdowns like "5h to Fajr").
     */
    fun parseTodayWidget(html: String, today: LocalDate): TimetableRow? {
        val text = htmlToText(html)
        val names = nameRx.findAll(text).mapNotNull { m -> nameToPrayer(m.value)?.let { it to m } }.toList()
        for (startIdx in names.indices) {
            if (names[startIdx].first != Prayer.FAJR) continue
            val begins = mutableMapOf<Prayer, LocalTime>()
            val jamaat = mutableMapOf<Prayer, LocalTime>()
            var expected = 0 // index into six
            var i = startIdx
            while (i < names.size && expected < six.size) {
                val (p, m) = names[i]
                val want = six[expected]
                if (p != want) {
                    if (want == Prayer.SUNRISE && p == Prayer.DHUHR) { expected++; continue } // sunrise is optional
                    break
                }
                val segEnd = if (i + 1 < names.size) names[i + 1].second.range.first else minOf(text.length, m.range.last + 120)
                val seg = text.substring(m.range.last + 1, segEnd).take(120)
                val times = timeRx.findAll(seg).mapNotNull { TimetableCsv.parseTime(it.groupValues[1] + it.groupValues[2], p) }.take(2).toList()
                if (times.isEmpty()) break
                if (p == Prayer.SUNRISE) begins[p] = times[0]
                else if (times.size == 1) {
                    begins[p] = times[0]
                    // A lone "Jamaat:" time (Manchester's Maghrib) is both begin and jamaat.
                    if (Regex("(?i)jama").containsMatchIn(seg)) jamaat[p] = times[0]
                } else {
                    val (a, b) = times
                    if (b.isBefore(a)) { begins[p] = b; jamaat[p] = a } else { begins[p] = a; jamaat[p] = b }
                }
                expected++; i++
            }
            if (begins.keys.containsAll(five) && isChronological(begins)) return TimetableRow(today, begins, jamaat)
        }
        return null
    }

    /** Masjidbox public pages are "today" boxes too. */
    fun parseMasjidbox(html: String, today: LocalDate): TimetableRow? = parseTodayWidget(html, today)

    private fun isChronological(b: Map<Prayer, LocalTime>): Boolean =
        five.mapNotNull { b[it] }.zipWithNext().all { (x, y) -> x.isBefore(y) }

    // ------------------------------------------------------------------ Mawaqit

    /**
     * Mawaqit mosque pages embed `var confData = {...};` containing:
     *  - "calendar": 12 month objects, each { "1": ["05:30","07:10","12:50","15:10","17:20","18:50"], ... }
     *  - "iqamaCalendar": same shape with 5 entries per day, either "+10" (minutes after begin) or "HH:mm".
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
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&#8217;", "'")
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
