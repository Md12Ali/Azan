package com.md12ali.sakinah.core.timetable

import com.md12ali.sakinah.core.prayer.Prayer
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * One row of a mosque's printed timetable (begin + jamaat times), stored as plain LocalTime so the
 * same row works in any time zone (UK clocks change in March/October – the mosque PDF already accounts for it).
 */
data class TimetableRow(
    val date: LocalDate,
    val begins: Map<Prayer, LocalTime>,
    val jamaat: Map<Prayer, LocalTime>
)

/**
 * Parses a simple CSV you can type from the mosque's monthly PDF (Luton Central Mosque publishes one each month).
 *
 * Header (column order is flexible, names are case-insensitive, jamaat columns optional):
 *   date,fajr,fajr_jamaat,sunrise,dhuhr,dhuhr_jamaat,asr,asr_jamaat,maghrib,maghrib_jamaat,isha,isha_jamaat
 *
 * - Dates: 2026-11-23, 23/11/2026 or 23-11-2026
 * - Times: 24h "13:05" or the 12h-without-AM/PM style many UK mosque PDFs use ("1:05" for Dhuhr,
 *   "7:15" for Isha) – afternoon prayers are converted to PM automatically.
 * - Lines starting with # are comments.
 */
object TimetableCsv {

    private val columnMap: Map<String, Pair<Prayer, Boolean>> = buildMap {
        Prayer.entries.forEach { p ->
            val key = p.english.lowercase()
            put(key, p to false)
            put("${key}_begin", p to false)
            put("${key}_start", p to false)
            put("${key}_jamaat", p to true)
            put("${key}_jamat", p to true)
            put("${key}_iqamah", p to true)
        }
        put("zuhr", Prayer.DHUHR to false); put("zuhr_jamaat", Prayer.DHUHR to true)
        put("shuruq", Prayer.SUNRISE to false)
    }

    private val dateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy")
    )

    fun parse(text: String): List<TimetableRow> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().split(',').map { it.trim().lowercase().replace(' ', '_') }
        val dateIdx = header.indexOf("date")
        require(dateIdx >= 0) { "CSV needs a 'date' column" }

        return lines.drop(1).mapNotNull { line ->
            val cells = line.split(',').map { it.trim() }
            val date = parseDate(cells.getOrNull(dateIdx) ?: return@mapNotNull null) ?: return@mapNotNull null
            val begins = mutableMapOf<Prayer, LocalTime>()
            val jamaat = mutableMapOf<Prayer, LocalTime>()
            header.forEachIndexed { i, col ->
                val (prayer, isJamaat) = columnMap[col] ?: return@forEachIndexed
                val time = parseTime(cells.getOrNull(i).orEmpty(), prayer) ?: return@forEachIndexed
                if (isJamaat) jamaat[prayer] = time else begins[prayer] = time
            }
            if (begins.keys.containsAll(Prayer.adhanPrayers)) TimetableRow(date, begins, jamaat) else null
        }
    }

    private fun parseDate(s: String): LocalDate? =
        dateFormats.firstNotNullOfOrNull { f -> runCatching { LocalDate.parse(s, f) }.getOrNull() }

    internal fun parseTime(raw: String, prayer: Prayer): LocalTime? {
        val s = raw.lowercase().replace(".", ":").trim()
        if (s.isEmpty()) return null
        val pm = s.endsWith("pm"); val am = s.endsWith("am")
        val parts = s.removeSuffix("pm").removeSuffix("am").trim().split(':')
        var h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        when {
            pm && h < 12 -> h += 12
            am && h == 12 -> h = 0
            !am && !pm -> {
                // 12h timetable without AM/PM: lift afternoon prayers into the afternoon.
                if (prayer == Prayer.DHUHR && h in 1..6) h += 12
                if (prayer in setOf(Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA) && h in 1..11) h += 12
            }
        }
        return runCatching { LocalTime.of(h, m) }.getOrNull()
    }
}
