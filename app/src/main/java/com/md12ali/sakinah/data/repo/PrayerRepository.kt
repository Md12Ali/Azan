package com.md12ali.sakinah.data.repo

import com.md12ali.sakinah.core.location.MosqueProfile
import com.md12ali.sakinah.core.location.Places
import com.md12ali.sakinah.core.prayer.CalcParams
import com.md12ali.sakinah.core.prayer.Offsets
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.core.prayer.PrayerCalculator
import com.md12ali.sakinah.core.prayer.PrayerDay
import com.md12ali.sakinah.core.prayer.TimeSource
import com.md12ali.sakinah.data.local.TimetableDao
import com.md12ali.sakinah.data.local.TimetableDayEntity
import com.md12ali.sakinah.data.remote.TimetableFetcher
import com.md12ali.sakinah.data.settings.AppSettings
import com.md12ali.sakinah.data.settings.LocationMode
import com.md12ali.sakinah.data.settings.SettingsRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What the app is currently following – shown at the top of the dashboard. */
data class ActivePlace(
    val title: String,                 // "Luton Central Mosque"
    val subtitle: String,              // "Nearest central mosque · 1.2 km"
    val mosque: MosqueProfile?,
    val latitude: Double,
    val longitude: Double
)

/**
 * Single source of truth for prayer times.
 *   Mosque (GPS-nearest or chosen) → its cached published timetable → else calculated for that mosque
 *   City/GPS point with no mosque nearby → calculated for that exact point.
 * Calculation is fully offline and matches api.aladhan.com to the minute, so no network is ever required.
 */
class PrayerRepository(
    private val settingsRepo: SettingsRepository,
    private val dao: TimetableDao,
    private val fetcher: TimetableFetcher,
    private val nearby: NearbyMosqueRepository
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    suspend fun activePlace(): ActivePlace = activePlace(settingsRepo.current())

    fun activePlace(s: AppSettings): ActivePlace {
        val london = Places.mosqueById("east-london-mosque")!!
        return when (s.mode) {
            LocationMode.MOSQUE -> {
                val found = com.md12ali.sakinah.core.location.NearbyMosque.decode(s.savedMosque)?.takeIf { it.id == s.mosqueId }
                val m = Places.mosqueById(s.mosqueId) ?: found?.let { nearby.toProfile(it, s.gpsCountryCode) } ?: Places.mosques.first()
                ActivePlace(m.name, if (found != null) "Your chosen mosque" else "Chosen mosque · ${m.city}", m, m.latitude, m.longitude)
            }
            LocationMode.CITY_CALC -> ActivePlace(
                s.cityName ?: "London", "Calculated · ${s.effectiveMethod.label}", null,
                s.cityLat ?: london.latitude, s.cityLon ?: london.longitude
            )
            LocationMode.AUTO_GPS -> {
                val lat = s.lastLat; val lon = s.lastLon
                if (lat == null || lon == null) {
                    ActivePlace("Waiting for GPS…", "Using London until your location is found", london, london.latitude, london.longitude)
                } else {
                    val m = Places.nearestCentralMosque(lat, lon)
                    val auto = com.md12ali.sakinah.core.location.NearbyMosque.decode(s.autoMosque)
                        ?.takeIf { s.followNearestMosque && Places.distanceKm(lat, lon, it.latitude, it.longitude) <= 10.0 }
                    if (m == null && auto != null) {
                        val km = Places.distanceKm(lat, lon, auto.latitude, auto.longitude)
                        ActivePlace(auto.name, "Nearest mosque with a timetable · %.1f km".format(km),
                            nearby.toProfile(auto, s.gpsCountryCode), lat, lon)
                    } else if (m != null) {
                        val km = Places.distanceKm(lat, lon, m.latitude, m.longitude)
                        ActivePlace(m.name, "Nearest central mosque · %.1f km".format(km), m, lat, lon)
                    } else {
                        ActivePlace(
                            s.gpsPlaceName ?: "Your location",
                            "GPS · ${s.effectiveMethod.label}", null, lat, lon
                        )
                    }
                }
            }
        }
    }

    /** Downloads the active mosque's timetable if the cache is getting short. Safe to call often. */
    suspend fun refreshTimetableIfNeeded(force: Boolean = false): String? {
        val s = settingsRepo.current()
        val mosque = activePlace(s).mosque ?: return null
        val today = LocalDate.now(zone)
        val cachedAhead = dao.futureDays(mosque.id, today.toString())
        // Masjidbox gives 1 day per fetch, Mawaqit/CSV a whole year – refetch when < 2 days are cached.
        if (!force && cachedAhead >= 2) return s.mosqueSources[mosque.id]
        val worked = fetcher.fetch(mosque, s.mosqueSources[mosque.id], today, hanafiAsr = s.effectiveMadhab == com.md12ali.sakinah.core.prayer.Madhab.HANAFI)
        if (worked != null && worked != s.mosqueSources[mosque.id]) settingsRepo.setMosqueSource(mosque.id, worked)
        return worked
    }

    suspend fun day(date: LocalDate): Pair<PrayerDay, ActivePlace> {
        val s = settingsRepo.current()
        val place = activePlace(s)
        val mosque = place.mosque
        if (mosque != null && !(s.mode == LocationMode.AUTO_GPS && s.lastLat == null)) {
            dao.day(mosque.id, date.toString())?.let { return it.toPrayerDay(date, mosque) to place }
            val calc = PrayerCalculator.calculate(
                date, mosque.latitude, mosque.longitude, zone,
                CalcParams(if (mosque.id.startsWith("osm_") || mosque.id.startsWith("mawaqit_")) s.effectiveMethod else mosque.method,
                    s.effectiveMadhab, mosque.fallbackOffsets)
            )
            return calc.copy(sourceDetail = if (mosque.sources.isEmpty()) "${mosque.name} doesn't publish a timetable online · calculated (${s.effectiveMethod.label})"
                else "Timetable not downloaded yet · calculated for ${mosque.name}") to place
        }
        val calc = PrayerCalculator.calculate(date, place.latitude, place.longitude, zone,
            CalcParams(s.effectiveMethod, s.effectiveMadhab, Offsets()))
        return calc to place
    }

    private fun TimetableDayEntity.toPrayerDay(date: LocalDate, mosque: MosqueProfile): PrayerDay {
        fun t(v: String?) = v?.let { date.atTime(LocalTime.parse(it)).atZone(zone) }
        val begins = buildMap {
            put(Prayer.FAJR, t(fajr)!!)
            // Sunrise isn't always published – calculate it if missing.
            put(Prayer.SUNRISE, t(sunrise) ?: PrayerCalculator.calculate(date, mosque.latitude, mosque.longitude, zone).begins.getValue(Prayer.SUNRISE))
            put(Prayer.DHUHR, t(dhuhr)!!); put(Prayer.ASR, t(asr)!!)
            put(Prayer.MAGHRIB, t(maghrib)!!); put(Prayer.ISHA, t(isha)!!)
        }
        val jamaat = buildMap {
            t(fajrJ)?.let { put(Prayer.FAJR, it) }; t(dhuhrJ)?.let { put(Prayer.DHUHR, it) }
            t(asrJ)?.let { put(Prayer.ASR, it) }; t(maghribJ)?.let { put(Prayer.MAGHRIB, it) }
            t(ishaJ)?.let { put(Prayer.ISHA, it) }
        }
        return PrayerDay(date, begins, jamaat, TimeSource.MOSQUE_TIMETABLE,
            "${mosque.name} timetable · via ${TimetableFetcher.describe(source)}")
    }
}
