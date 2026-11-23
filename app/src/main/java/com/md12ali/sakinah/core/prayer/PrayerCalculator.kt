package com.md12ali.sakinah.core.prayer

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Calculation methods. UK-friendly default is MOONSIGHTING_COMMITTEE (Khalid Shaukat), which handles the
 * very short summer nights in Britain with seasonal Fajr/Isha adjustments instead of impossible 18° angles.
 * The [aladhanId] lets the same choice be sent to the AlAdhan API so online and offline results match.
 */
enum class CalculationMethod(
    val label: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val aladhanId: Int,
    /** Isha = Maghrib + N minutes (Umm al-Qura, Gulf, Qatar) instead of an angle. */
    val ishaIntervalMin: Int? = null,
    /** Fixed minute adjustments the authority publishes (e.g. Diyanet). */
    val methodOffsets: Offsets = Offsets()
) {
    MOONSIGHTING_COMMITTEE("Moonsighting Committee (UK)", 18.0, 18.0, 15),
    MUSLIM_WORLD_LEAGUE("Muslim World League", 18.0, 17.0, 3),
    UMM_AL_QURA("Umm al-Qura, Makkah", 18.5, 0.0, 4, ishaIntervalMin = 90),
    EGYPT("Egyptian General Authority", 19.5, 17.5, 5),
    KARACHI("Karachi (Pakistan, India, Bangladesh)", 18.0, 18.0, 1),
    ISNA("ISNA (North America)", 15.0, 15.0, 2),
    GULF("Gulf region", 19.5, 0.0, 8, ishaIntervalMin = 90),
    KUWAIT("Kuwait", 18.0, 17.5, 9),
    QATAR("Qatar", 18.0, 0.0, 10, ishaIntervalMin = 90),
    DUBAI("Dubai (UAE)", 18.2, 18.2, 16),
    SINGAPORE("Singapore / Malaysia (JAKIM)", 20.0, 18.0, 11),
    INDONESIA("Indonesia (Kemenag)", 20.0, 18.0, 20),
    TURKEY("Diyanet, Turkey", 18.0, 17.0, 13, methodOffsets = Offsets(sunrise = -7, dhuhr = 5, asr = 4, maghrib = 7)),
    FRANCE("UOIF, France", 12.0, 12.0, 12),
    RUSSIA("Russia", 16.0, 15.0, 14),
    MOROCCO("Morocco", 19.0, 17.0, 21),
    TUNISIA("Tunisia", 18.0, 18.0, 18),
    ALGERIA("Algeria", 18.0, 17.0, 19),
    JORDAN("Jordan", 18.0, 18.0, 23),
    IRAN("Tehran (Shia)", 17.7, 14.0, 7);

    companion object {
        /** The method most mosques use in a country (ISO 3166 alpha-2 code). */
        fun forCountry(code: String?): CalculationMethod = when (code?.uppercase()) {
            "GB", "IE" -> MOONSIGHTING_COMMITTEE
            "SA", "YE" -> UMM_AL_QURA
            "AE" -> DUBAI
            "OM", "BH" -> GULF
            "KW" -> KUWAIT
            "QA" -> QATAR
            "EG", "SD", "LY", "SY", "LB", "IQ", "PS" -> EGYPT
            "PK", "IN", "BD", "AF", "NP", "LK" -> KARACHI
            "US", "CA", "MX" -> ISNA
            "MY", "SG", "BN" -> SINGAPORE
            "ID" -> INDONESIA
            "TR", "AZ", "XK" -> TURKEY
            "FR", "BE", "LU" -> FRANCE
            "RU", "KZ", "UZ", "TJ", "KG", "TM" -> RUSSIA
            "MA" -> MOROCCO
            "TN" -> TUNISIA
            "DZ" -> ALGERIA
            "JO" -> JORDAN
            "IR" -> IRAN
            else -> MUSLIM_WORLD_LEAGUE
        }

        /** Hanafi Asr is standard in South Asia, Turkey, Central Asia and UK South-Asian communities. */
        fun defaultMadhabFor(code: String?): Madhab = when (code?.uppercase()) {
            "PK", "IN", "BD", "AF", "TR", "GB", "UZ", "TJ", "KG", "KZ", "TM", "AZ", "XK", "BA", "AL" -> Madhab.HANAFI
            else -> Madhab.SHAFI
        }
    }
}

/** Per-prayer offsets in minutes, e.g. to align with a mosque that adds safety minutes. */
data class Offsets(
    val fajr: Int = 0, val sunrise: Int = 0, val dhuhr: Int = 0,
    val asr: Int = 0, val maghrib: Int = 0, val isha: Int = 0
) {
    fun of(p: Prayer) = when (p) {
        Prayer.FAJR -> fajr; Prayer.SUNRISE -> sunrise; Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr; Prayer.MAGHRIB -> maghrib; Prayer.ISHA -> isha
    }
}

data class CalcParams(
    val method: CalculationMethod = CalculationMethod.MOONSIGHTING_COMMITTEE,
    val madhab: Madhab = Madhab.HANAFI,
    val offsets: Offsets = Offsets()
)

/**
 * Fully offline astronomical prayer-time calculator (no network, no third-party library).
 * Solar position follows the well-known PrayTimes.org formulas (≈1 min accuracy); the Moonsighting
 * Committee seasonal twilight follows Khalid Shaukat's published model (same as the Adhan library).
 * Verified against api.aladhan.com (method=15, school=1) for Luton – see core self-test in README.
 */
object PrayerCalculator {

    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        params: CalcParams = CalcParams()
    ): PrayerDay {
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - longitude / (15.0 * 24.0)

        // Two passes: first with rough guesses, then refined using the first results (PrayTimes approach).
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0, 18.0) // fajr, sunrise, dhuhr, asr, sunset, maghrib, isha
        repeat(2) { t = computeRaw(jd, latitude, params, t.map { it / 24.0 }.toDoubleArray()) }

        // Convert local-solar hours to UTC hours.
        val utc = t.map { it - longitude / 15.0 }.toDoubleArray()
        var fajr = utc[0]; val sunrise = utc[1]; val dhuhr = utc[2]; val asr = utc[3]
        val sunset = utc[4]; var isha = utc[6]
        val night = 24.0 - (sunset - sunrise)

        when (params.method) {
            CalculationMethod.MOONSIGHTING_COMMITTEE -> {
                if (abs(latitude) >= 55.0) {
                    // Above 55° (e.g. north Scotland) MCW uses 1/7th of the night.
                    fajr = sunrise - night / 7.0
                    isha = sunset + night / 7.0
                } else {
                    val dyy = daysSinceSolstice(date, latitude)
                    val safeFajr = sunrise - seasonalMorning(latitude, dyy) / 60.0
                    val safeIsha = sunset + seasonalEvening(latitude, dyy) / 60.0
                    if (fajr.isNaN() || fajr < safeFajr) fajr = safeFajr
                    if (isha.isNaN() || isha > safeIsha) isha = safeIsha
                }
            }
            else -> {
                params.method.ishaIntervalMin?.let { isha = sunset + it / 60.0 }
                // "Angle based" high-latitude rule (AlAdhan default): portion of night = angle / 60.
                val fajrPortion = params.method.fajrAngle / 60.0 * night
                if (fajr.isNaN() || sunrise - fajr > fajrPortion) fajr = sunrise - fajrPortion
                if (params.method.ishaIntervalMin == null) {
                    val ishaPortion = params.method.ishaAngle / 60.0 * night
                    if (isha.isNaN() || isha - sunset > ishaPortion) isha = sunset + ishaPortion
                }
            }
        }

        val raw = mapOf(
            Prayer.FAJR to fajr, Prayer.SUNRISE to sunrise, Prayer.DHUHR to dhuhr,
            Prayer.ASR to asr, Prayer.MAGHRIB to sunset, Prayer.ISHA to isha
        )
        val midnightUtc = date.atStartOfDay(ZoneOffset.UTC)
        val begins = raw.mapValues { (p, hours) ->
            val seconds = (hours * 3600.0).roundToLong() + (params.offsets.of(p) + params.method.methodOffsets.of(p)) * 60L
            roundToMinute(midnightUtc.plusSeconds(seconds), up = p == Prayer.ASR).withZoneSameInstant(zone)
        }
        return PrayerDay(
            date = date,
            begins = begins,
            source = TimeSource.CALCULATED,
            sourceDetail = "${params.method.label} · ${if (params.madhab == Madhab.HANAFI) "Hanafi" else "Shafi'i"} Asr"
        )
    }

    // ---------------------------------------------------------------- astronomy

    private fun computeRaw(jd: Double, lat: Double, p: CalcParams, dayPortion: DoubleArray): DoubleArray {
        val fajr = sunAngleTime(jd, lat, p.method.fajrAngle, dayPortion[0], ccw = true)
        val sunrise = sunAngleTime(jd, lat, RISE_SET_ANGLE, dayPortion[1], ccw = true)
        val dhuhr = midDay(jd, dayPortion[2])
        val asr = asrTime(jd, lat, p.madhab.shadowFactor.toDouble(), dayPortion[3])
        val sunset = sunAngleTime(jd, lat, RISE_SET_ANGLE, dayPortion[4], ccw = false)
        val isha = sunAngleTime(jd, lat, p.method.ishaAngle, dayPortion[6], ccw = false)
        return doubleArrayOf(fajr, sunrise, dhuhr, asr, sunset, sunset, isha)
    }

    private const val RISE_SET_ANGLE = 0.833

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year; var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private data class SunPos(val declination: Double, val equation: Double)

    private fun sunPosition(jd: Double): SunPos {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dSin(g) + 0.020 * dSin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(dAtan2(dCos(e) * dSin(l), dCos(l)) / 15.0)
        val eqt = q / 15.0 - ra
        val decl = dAsin(dSin(e) * dSin(l))
        return SunPos(decl, eqt)
    }

    private fun midDay(jd: Double, t: Double): Double = fixHour(12.0 - sunPosition(jd + t).equation)

    private fun sunAngleTime(jd: Double, lat: Double, angle: Double, t: Double, ccw: Boolean): Double {
        val decl = sunPosition(jd + t).declination
        val noon = midDay(jd, t)
        val cosV = (-dSin(angle) - dSin(decl) * dSin(lat)) / (dCos(decl) * dCos(lat))
        if (cosV < -1.0 || cosV > 1.0) return Double.NaN // sun never reaches this angle (UK summer!)
        val h = dAcos(cosV) / 15.0
        return noon + if (ccw) -h else h
    }

    private fun asrTime(jd: Double, lat: Double, factor: Double, t: Double): Double {
        val decl = sunPosition(jd + t).declination
        val angle = -dAcot(factor + dTan(abs(lat - decl)))
        return sunAngleTime(jd, lat, angle, t, ccw = false)
    }

    // ------------------------------------------------- Moonsighting Committee

    private fun daysSinceSolstice(date: LocalDate, latitude: Double): Int {
        val daysInYear = if (date.isLeapYear) 366 else 365
        val dayOfYear = date.dayOfYear
        return if (latitude >= 0) {
            var d = dayOfYear + 10
            if (d >= daysInYear) d -= daysInYear
            d
        } else {
            var d = dayOfYear - if (date.isLeapYear) 173 else 172
            if (d < 0) d += daysInYear
            d
        }
    }

    /** Minutes before sunrise. */
    private fun seasonalMorning(lat: Double, dyy: Int): Double {
        val a = 75 + 28.65 / 55.0 * abs(lat)
        val b = 75 + 19.44 / 55.0 * abs(lat)
        val c = 75 + 32.74 / 55.0 * abs(lat)
        val d = 75 + 48.10 / 55.0 * abs(lat)
        return piecewise(dyy, a, b, c, d)
    }

    /** Minutes after sunset ("general" shafaq). */
    private fun seasonalEvening(lat: Double, dyy: Int): Double {
        val a = 75 + 25.60 / 55.0 * abs(lat)
        val b = 75 + 2.050 / 55.0 * abs(lat)
        val c = 75 - 9.210 / 55.0 * abs(lat)
        val d = 75 + 6.140 / 55.0 * abs(lat)
        return piecewise(dyy, a, b, c, d)
    }

    private fun piecewise(dyy: Int, a: Double, b: Double, c: Double, d: Double): Double = when {
        dyy < 91 -> a + (b - a) / 91.0 * dyy
        dyy < 137 -> b + (c - b) / 46.0 * (dyy - 91)
        dyy < 183 -> c + (d - c) / 46.0 * (dyy - 137)
        dyy < 229 -> d + (c - d) / 46.0 * (dyy - 183)
        dyy < 275 -> c + (b - c) / 46.0 * (dyy - 229)
        else -> b + (a - b) / 91.0 * (dyy - 275)
    }

    // ----------------------------------------------------------------- helpers

    /** Nearest minute; Asr is rounded up (never announce Asr before it has truly begun – matches AlAdhan). */
    private fun roundToMinute(t: ZonedDateTime, up: Boolean = false): ZonedDateTime =
        if ((up && (t.second > 0 || t.nano > 0)) || t.second >= 30) t.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        else t.truncatedTo(ChronoUnit.MINUTES)

    private fun dSin(d: Double) = sin(Math.toRadians(d))
    private fun dCos(d: Double) = cos(Math.toRadians(d))
    private fun dTan(d: Double) = tan(Math.toRadians(d))
    private fun dAsin(x: Double) = Math.toDegrees(asin(x))
    private fun dAcos(x: Double) = Math.toDegrees(acos(x))
    private fun dAtan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun dAcot(x: Double) = Math.toDegrees(atan(1.0 / x))
    private fun fixAngle(a: Double) = (a - 360.0 * floor(a / 360.0))
    private fun fixHour(a: Double) = (a - 24.0 * floor(a / 24.0))
}
