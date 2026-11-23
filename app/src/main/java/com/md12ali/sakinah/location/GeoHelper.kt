package com.md12ali.sakinah.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.md12ali.sakinah.core.location.City
import com.md12ali.sakinah.core.location.Places
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Worldwide place search and "which country am I in?" using Android's built-in Geocoder (free, no API key).
 * Falls back to the app's built-in world city list when the phone has no geocoder or no internet.
 */
class GeoHelper(private val context: Context) {

    data class PlaceInfo(val locality: String?, val countryCode: String?, val country: String?)

    private fun geocoder() = Geocoder(context, Locale.ENGLISH)

    /** Search any city / town / village in the world by name. */
    suspend fun search(query: String): List<City> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val offline = (Places.worldCities + Places.ukCities)
            .filter { it.name.contains(q, ignoreCase = true) || (it.country?.contains(q, ignoreCase = true) == true) }
        val online = if (Geocoder.isPresent()) withTimeoutOrNull(8_000) {
            runCatching {
                @Suppress("DEPRECATION")
                geocoder().getFromLocationName(q, 8).orEmpty().mapNotNull { it.toCity() }
            }.getOrDefault(emptyList())
        }.orEmpty() else emptyList()
        (online + offline).distinctBy { "${it.name.lowercase()}|${it.countryCode}" }.take(20)
    }

    /** Country + town for a GPS fix (used to pick the right calculation method automatically). */
    suspend fun reverse(lat: Double, lon: Double): PlaceInfo = withContext(Dispatchers.IO) {
        val a = if (Geocoder.isPresent()) withTimeoutOrNull(8_000) {
            runCatching {
                @Suppress("DEPRECATION")
                geocoder().getFromLocation(lat, lon, 1)?.firstOrNull()
            }.getOrNull()
        } else null
        if (a != null) PlaceInfo(a.locality ?: a.subAdminArea ?: a.adminArea, a.countryCode, a.countryName)
        else {
            // Offline fallback: nearest known city (good enough for the country/method).
            val c = (Places.worldCities + Places.ukCities).minBy { Places.distanceKm(lat, lon, it.latitude, it.longitude) }
            val near = Places.distanceKm(lat, lon, c.latitude, c.longitude) < 300
            PlaceInfo(if (near) c.name else null, if (near) c.countryCode else null, if (near) c.country else null)
        }
    }

    private fun Address.toCity(): City? {
        if (!hasLatitude() || !hasLongitude()) return null
        val name = locality ?: subAdminArea ?: featureName ?: adminArea ?: return null
        val region = listOfNotNull(adminArea?.takeIf { it != name }, countryName).joinToString(", ")
        return City(name + if (region.isNotBlank()) ", $region" else "", latitude, longitude, countryCode, countryName)
    }
}
