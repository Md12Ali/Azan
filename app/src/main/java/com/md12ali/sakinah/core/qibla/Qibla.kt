package com.md12ali.sakinah.core.qibla

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Qibla direction from anywhere on Earth (great-circle bearing to the Kaaba, Makkah).
 * Works fully offline – only the user's latitude/longitude are needed.
 */
object Qibla {
    const val KAABA_LAT = 21.422487
    const val KAABA_LON = 39.826206

    /** Bearing in degrees clockwise from TRUE north (0..360). */
    fun bearing(lat: Double, lon: Double): Double {
        val phi1 = Math.toRadians(lat)
        val phi2 = Math.toRadians(KAABA_LAT)
        val dLon = Math.toRadians(KAABA_LON - lon)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Great-circle distance to the Kaaba in km. */
    fun distanceKm(lat: Double, lon: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(KAABA_LAT - lat)
        val dLon = Math.toRadians(KAABA_LON - lon)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(KAABA_LAT)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /** "N", "NE", "ESE"… for a bearing. */
    fun compassPoint(deg: Double): String {
        val pts = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return pts[(((deg % 360) + 360) % 360 / 22.5 + 0.5).toInt() % 16]
    }

    /** Signed smallest angle a→b in degrees (-180..180). Positive = turn right. */
    fun delta(from: Double, to: Double): Double = ((to - from + 540.0) % 360.0) - 180.0
}
