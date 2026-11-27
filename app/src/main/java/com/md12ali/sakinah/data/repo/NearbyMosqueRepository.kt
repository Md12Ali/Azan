package com.md12ali.sakinah.data.repo

import com.md12ali.sakinah.core.location.MosqueProfile
import com.md12ali.sakinah.core.location.NearbyMosque
import com.md12ali.sakinah.core.location.NearbyMosqueParsers
import com.md12ali.sakinah.core.location.Places
import com.md12ali.sakinah.core.location.TimetableSource
import com.md12ali.sakinah.core.location.UK_MOSQUE_OFFSETS
import com.md12ali.sakinah.core.prayer.CalculationMethod
import com.md12ali.sakinah.core.prayer.Offsets
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.core.timetable.TimetableCsv
import com.md12ali.sakinah.data.local.TimetableDao
import com.md12ali.sakinah.data.local.TimetableDayEntity
import com.md12ali.sakinah.data.remote.Http
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

/**
 * Finds mosques near any point on Earth:
 *  • Mawaqit (mawaqit.net) – mosques that publish their OWN timetable (begin + jamaat), 85+ countries
 *  • OpenStreetMap (Overpass API) – every mosque on the map, worldwide, free, no key
 *  • the app's curated UK central mosques (East London, Birmingham, Luton…)
 * Results are merged, de-duplicated and sorted by distance.
 */
class NearbyMosqueRepository(private val dao: TimetableDao) {

    private var cache: Triple<Double, Double, List<NearbyMosque>>? = null

    suspend fun find(lat: Double, lon: Double, force: Boolean = false): List<NearbyMosque> {
        cache?.let { (cLat, cLon, list) ->
            if (!force && Places.distanceKm(lat, lon, cLat, cLon) < 0.5 && list.isNotEmpty()) return list
        }
        val result = coroutineScope {
            val mawaqit = async { searchMawaqit(lat, lon) }
            val osm = async {
                // Start close; widen the circle in rural areas with few mosques.
                var r = searchOsm(lat, lon, 5_000)
                if (r.size < 3) r = searchOsm(lat, lon, 25_000)
                if (r.isEmpty()) r = searchOsm(lat, lon, 80_000)
                r
            }
            val central = Places.mosques
                .map { NearbyMosque("central_${it.id}", it.name, it.latitude, it.longitude, it.address, it.website, null,
                    distanceKm = Places.distanceKm(lat, lon, it.latitude, it.longitude), source = "uk-central") }
                .filter { it.distanceKm <= 30 }
            NearbyMosqueParsers.merge(mawaqit.await().filter { it.distanceKm <= 80 }, osm.await(), central).take(60)
        }
        if (result.isNotEmpty()) cache = Triple(lat, lon, result)
        return result
    }

    private suspend fun searchMawaqit(lat: Double, lon: Double): List<NearbyMosque> =
        Http.get("https://mawaqit.net/api/2.0/mosque/search?lat=$lat&lon=$lon", 12_000)
            ?.let { NearbyMosqueParsers.parseMawaqitSearch(it, lat, lon) }.orEmpty()

    private suspend fun searchOsm(lat: Double, lon: Double, radiusM: Int): List<NearbyMosque> {
        val q = NearbyMosqueParsers.overpassQuery(lat, lon, radiusM)
        for (server in OVERPASS_SERVERS) {
            val json = Http.postForm(server, "data", q) ?: continue
            return NearbyMosqueParsers.parseOverpass(json, lat, lon)
        }
        return emptyList()
    }

    /** Turns a found mosque into a profile the prayer-time engine can follow. */
    fun toProfile(m: NearbyMosque, countryCode: String?): MosqueProfile {
        Places.mosqueById(m.id.removePrefix("central_"))?.let { return it }
        return MosqueProfile(
            id = m.id,
            city = m.address ?: "",
            name = m.name,
            address = m.address ?: "",
            latitude = m.latitude,
            longitude = m.longitude,
            sources = listOfNotNull(
                m.mawaqitSlug?.let { TimetableSource.Mawaqit(it) },
                m.website?.let { TimetableSource.DiscoverFromWebsite(it) }
            ),
            method = CalculationMethod.forCountry(countryCode),
            madhab = CalculationMethod.defaultMadhabFor(countryCode),
            fallbackOffsets = if (countryCode.equals("GB", true)) UK_MOSQUE_OFFSETS else Offsets(),
            website = m.website,
            coordinatesApproximate = false
        )
    }

    /** If the full Mawaqit calendar can't be read, keep today's times from the search result. */
    suspend fun saveTodayFromSearch(m: NearbyMosque, date: LocalDate = LocalDate.now()) {
        val t = m.todayTimes
        val order = when (t.size) {
            5 -> listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
            6 -> listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
            else -> return
        }
        val map = order.zip(t).mapNotNull { (p, v) -> TimetableCsv.parseTime(v, p)?.let { p to it.toString() } }.toMap()
        if (map.size != order.size) return
        dao.upsert(listOf(TimetableDayEntity(
            mosqueId = m.id, date = date.toString(),
            fajr = map.getValue(Prayer.FAJR), sunrise = map[Prayer.SUNRISE], dhuhr = map.getValue(Prayer.DHUHR),
            asr = map.getValue(Prayer.ASR), maghrib = map.getValue(Prayer.MAGHRIB), isha = map.getValue(Prayer.ISHA),
            fajrJ = null, dhuhrJ = null, asrJ = null, maghribJ = null, ishaJ = null,
            source = "mawaqit:${m.mawaqitSlug}", fetchedAt = System.currentTimeMillis()
        )))
    }

    companion object {
        val OVERPASS_SERVERS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
    }
}
