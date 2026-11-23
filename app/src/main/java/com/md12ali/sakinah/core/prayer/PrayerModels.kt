package com.md12ali.sakinah.core.prayer

import java.time.LocalDate
import java.time.ZonedDateTime

/** The five daily prayers plus Sunrise (shown on the dashboard, never plays an Adhan). */
enum class Prayer(val english: String, val arabic: String, val hasAdhan: Boolean) {
    FAJR("Fajr", "الفجر", true),
    SUNRISE("Sunrise", "الشروق", false),
    DHUHR("Dhuhr", "الظهر", true),
    ASR("Asr", "العصر", true),
    MAGHRIB("Maghrib", "المغرب", true),
    ISHA("Isha", "العشاء", true);

    companion object {
        val adhanPrayers = entries.filter { it.hasAdhan }
    }
}

/** Juristic school – only changes Asr (shadow length 1x for Shafi'i/Maliki/Hanbali, 2x for Hanafi). */
enum class Madhab(val label: String, val shadowFactor: Int) {
    SHAFI("Shafi'i / Maliki / Hanbali (earlier Asr)", 1),
    HANAFI("Hanafi (later Asr)", 2)
}

/** Where a day's times came from – shown as a small chip on the dashboard so users can trust the numbers. */
enum class TimeSource(val label: String) {
    MOSQUE_TIMETABLE("Mosque timetable"),
    ONLINE_CACHE("AlAdhan (cached)"),
    CALCULATED("Calculated on device")
}

/**
 * One day of prayer times.
 * @param begins start time of each prayer (always present).
 * @param jamaat congregation times – only mosque timetables have these.
 */
data class PrayerDay(
    val date: LocalDate,
    val begins: Map<Prayer, ZonedDateTime>,
    val jamaat: Map<Prayer, ZonedDateTime> = emptyMap(),
    val source: TimeSource,
    val sourceDetail: String
) {
    fun nextAfter(now: ZonedDateTime, adhanOnly: Boolean = false): Pair<Prayer, ZonedDateTime>? =
        begins.entries
            .filter { !adhanOnly || it.key.hasAdhan }
            .sortedBy { it.value }
            .firstOrNull { it.value.isAfter(now) }
            ?.toPair()

    fun currentAt(now: ZonedDateTime): Prayer? =
        begins.entries.sortedBy { it.value }.lastOrNull { !it.value.isAfter(now) }?.key
}
