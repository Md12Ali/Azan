package com.md12ali.sakinah.core.location

import java.net.URLDecoder

/**
 * Reads a place out of a Google Maps link (the one you get with Share → Copy link in Google Maps):
 *   https://www.google.com/maps/place/East+London+Mosque/@51.5176,-0.0652,17z/data=...!3d51.5175!4d-0.0651
 *   https://maps.google.com/?q=51.5176,-0.0652
 *   https://maps.app.goo.gl/AbC123  (short link – the app follows it first)
 * The pin (!3d…!4d…) is preferred over the map centre (@lat,lon) because it is the exact building.
 */
object GoogleMapsLink {

    data class Place(val name: String?, val latitude: Double, val longitude: Double)

    private val pinRx = Regex("""!3d(-?\d{1,3}\.\d+)!4d(-?\d{1,3}\.\d+)""")
    private val atRx = Regex("""@(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""")
    private val qRx = Regex("""[?&](?:q|query|ll|destination|center)=(-?\d{1,3}\.\d+)(?:,|%2C)\s*(-?\d{1,3}\.\d+)""", RegexOption.IGNORE_CASE)
    private val placeRx = Regex("""/maps/place/([^/@?]+)""")
    private val urlRx = Regex("""https?://\S+""")

    fun firstUrl(text: String): String? = urlRx.find(text)?.value?.trimEnd(')', '.', ',', '"', '\'')

    fun isGoogleMaps(url: String): Boolean =
        Regex("""(google\.[a-z.]+/maps|maps\.google\.|maps\.app\.goo\.gl|goo\.gl/maps|g\.co/kgs)""", RegexOption.IGNORE_CASE).containsMatchIn(url)

    fun parse(url: String): Place? {
        val (lat, lon) = (pinRx.find(url) ?: qRx.find(url) ?: atRx.find(url))
            ?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() } ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val name = placeRx.find(url)?.groupValues?.get(1)
            ?.let { runCatching { URLDecoder.decode(it.replace("+", " "), "UTF-8") }.getOrNull() }
            ?.takeIf { it.isNotBlank() && !Regex("""^-?\d""").containsMatchIn(it) }
        return Place(name, lat, lon)
    }

    /** Shared text often looks like "Masjid Al-Noor\nhttps://maps.app.goo.gl/…" – the first line is the name. */
    fun nameFromSharedText(text: String): String? =
        text.lines().map { it.trim() }.firstOrNull { it.isNotBlank() && !it.startsWith("http") }
            ?.takeIf { it.length in 3..120 }
}
