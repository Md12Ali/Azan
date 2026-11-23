package com.md12ali.sakinah.core.location

import com.md12ali.sakinah.core.prayer.CalculationMethod
import com.md12ali.sakinah.core.prayer.Madhab
import com.md12ali.sakinah.core.prayer.Offsets
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where a mosque publishes its own timetable. The app tries these in order and caches the result
 * for offline use (see TimetableFetcher in the Android layer).
 */
sealed interface TimetableSource {
    /** masjidbox.com/prayer-times/{slug} – used by Luton Central Mosque and many UK mosques. */
    data class Masjidbox(val slug: String) : TimetableSource
    /** mawaqit.net/en/{slug} – very common in the UK & Europe. */
    data class Mawaqit(val slug: String) : TimetableSource
    /** Any URL to a CSV in the TimetableCsv format (e.g. a Google Sheet "publish to web → CSV"). */
    data class CsvUrl(val url: String) : TimetableSource
    /** Fetch the mosque website and look for a Masjidbox / Mawaqit / CSV link automatically. */
    data class DiscoverFromWebsite(val url: String) : TimetableSource
    /** CSV bundled in app/src/main/assets (typed from the mosque's monthly PDF). */
    data class BundledCsv(val assetPath: String) : TimetableSource
    /** A web page with a dated month/year table; [layout] is a key of OnlineTimetableParsers.layouts. */
    data class HtmlTable(val url: String, val layout: String) : TimetableSource
    /** A web page with a "today" box (prayer name → begin → jamaat). */
    data class TodayWidget(val url: String) : TimetableSource
}

/**
 * A city's central mosque. [fallbackOffsets] are applied to the on-device calculation when no
 * published timetable can be fetched (UK mosques typically add ~5 min to Dhuhr and ~3 min to Maghrib).
 */
data class MosqueProfile(
    val id: String,
    val city: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val sources: List<TimetableSource>,
    val method: CalculationMethod = CalculationMethod.MOONSIGHTING_COMMITTEE,
    val madhab: Madhab = Madhab.HANAFI,
    val fallbackOffsets: Offsets = UK_MOSQUE_OFFSETS,
    val website: String? = null,
    val coordinatesApproximate: Boolean = false
)

data class City(val name: String, val latitude: Double, val longitude: Double)

val UK_MOSQUE_OFFSETS = Offsets(dhuhr = 5, maghrib = 3)

/** Slug guess used for automatic Masjidbox/Mawaqit discovery: "Birmingham Central Mosque" → "birmingham-central-mosque". */
fun slugify(name: String): String =
    name.lowercase().replace("&", "and").replace(Regex("[^a-z0-9]+"), "-").trim('-')

object Places {

    private fun mosque(
        city: String, name: String, address: String, lat: Double, lon: Double,
        website: String? = null, approx: Boolean = true, extra: List<TimetableSource> = emptyList()
    ): MosqueProfile {
        val slug = slugify(name)
        val sources = buildList {
            addAll(extra)
            website?.let { add(TimetableSource.DiscoverFromWebsite(it)) }
            // Automatic guesses – cheap to try, cached once one works.
            add(TimetableSource.Masjidbox(slug))
            add(TimetableSource.Mawaqit(slug))
        }.distinct()
        return MosqueProfile(
            id = slug, city = city, name = name, address = address, latitude = lat, longitude = lon,
            sources = sources, website = website, coordinatesApproximate = approx
        )
    }

    /**
     * Central / main mosques for UK cities. Luton is verified (Masjidbox page confirmed).
     * Others start with automatic discovery; coordinates marked approx are near the city's main mosque.
     * Users can also attach any Masjidbox / Mawaqit / CSV link to any mosque from the Location screen.
     */
    val mosques: List<MosqueProfile> = listOf(
        mosque("Luton", "Luton Central Mosque", "2-12 Westbourne Road, Luton LU4 8JD", 51.8838, -0.4339,
            website = "https://www.lutoncentralmosque.org/prayer-times/", approx = false,
            extra = listOf(
                TimetableSource.Masjidbox("luton-central-mosque"),
                TimetableSource.BundledCsv("timetables/luton_central_mosque.csv")
            )),
        // London → East London Mosque: whole-year table with begin + jamaat times.
        mosque("London", "East London Mosque", "82-92 Whitechapel Road, London E1 1JQ", 51.5176, -0.0652,
            website = "https://www.eastlondonmosque.org.uk/", approx = false,
            extra = listOf(
                TimetableSource.HtmlTable("https://www.eastlondonmosque.org.uk/prayer-times", "elm"),
                TimetableSource.TodayWidget("https://www.eastlondonmosque.org.uk/")
            )),
        // Birmingham Central Mosque: monthly table + today box on the home page.
        mosque("Birmingham", "Birmingham Central Mosque", "180 Belgrave Middleway, Birmingham B12 0XS", 52.4676, -1.8930,
            website = "https://centralmosque.org.uk/", approx = false,
            extra = listOf(
                TimetableSource.HtmlTable("https://centralmosque.org.uk/timetable", "bcm"),
                TimetableSource.TodayWidget("https://centralmosque.org.uk/")
            )),
        mosque("Leicester", "Leicester Central Mosque", "Conduit Street, Leicester LE2 0JN", 52.6290, -1.1220,
            website = "https://www.islamiccentre.org/",
            extra = listOf(TimetableSource.TodayWidget("https://www.islamiccentre.org/"))),
        mosque("Manchester", "Manchester Central Mosque", "20 Upper Park Road, Victoria Park, Manchester M14 5RU", 53.457191, -2.220343,
            website = "https://manchestercentralmosque.org/", approx = false,
            extra = listOf(TimetableSource.TodayWidget("https://manchestercentralmosque.org/"))),
        mosque("Glasgow", "Glasgow Central Mosque", "1 Mosque Avenue, Glasgow G5 9TA", 55.8530, -4.2490,
            website = "https://centralmosque.co.uk/"),
        mosque("Bradford", "Bradford Grand Mosque", "Bradford BD8", 53.8000, -1.7700),
        mosque("Leeds", "Leeds Grand Mosque", "Leeds LS6", 53.8150, -1.5650),
        mosque("Sheffield", "Sheffield Central Mosque", "Sheffield S3", 53.3880, -1.4800),
        mosque("Blackburn", "Blackburn Central Mosque", "Blackburn BB1", 53.7500, -2.4800),
        mosque("Bolton", "Bolton Central Mosque", "Bolton BL1", 53.5800, -2.4300),
        mosque("Slough", "Slough Central Mosque", "Slough SL1", 51.5120, -0.6000),
        mosque("High Wycombe", "High Wycombe Central Mosque", "High Wycombe HP13", 51.6300, -0.7400),
        mosque("Bedford", "Bedford Central Mosque", "Bedford MK40", 52.1360, -0.4700),
        mosque("Milton Keynes", "Milton Keynes Central Mosque", "Milton Keynes MK", 52.0400, -0.7600),
        mosque("Oxford", "Oxford Central Mosque", "Oxford OX4", 51.7450, -1.2300),
        mosque("Coventry", "Coventry Central Mosque", "Coventry CV1", 52.4100, -1.5100),
        mosque("Nottingham", "Nottingham Central Mosque", "Nottingham NG7", 52.9600, -1.1700),
        mosque("Liverpool", "Liverpool Central Mosque", "Liverpool L8", 53.3950, -2.9700),
        mosque("Newcastle", "Newcastle Central Mosque", "Newcastle upon Tyne NE4", 54.9700, -1.6400),
        mosque("Bristol", "Bristol Central Mosque", "Bristol BS5", 51.4600, -2.5700),
        mosque("Cardiff", "Cardiff Central Mosque", "Cardiff CF10", 51.4700, -3.1700),
        mosque("Brighton", "Brighton Central Mosque", "Brighton BN1", 50.8300, -0.1400),
        mosque("Edinburgh", "Edinburgh Central Mosque", "50 Potterrow, Edinburgh EH8 9BT", 55.9450, -3.1880,
            website = "https://www.edinburghcentralmosque.com")
    )

    /** Manual city list (used for Auto-GPS city naming and when a user prefers plain calculation). */
    val ukCities: List<City> = mosques.map { City(it.city, it.latitude, it.longitude) }.distinctBy { it.name }

    fun mosqueById(id: String?): MosqueProfile? = mosques.firstOrNull { it.id == id }

    /**
     * The central mosque the app should follow for a GPS fix: the nearest one within [maxKm].
     * Outside that radius (e.g. a village far from any listed city) the app calculates for the exact GPS point.
     */
    fun nearestCentralMosque(lat: Double, lon: Double, maxKm: Double = 25.0): MosqueProfile? =
        mosques.map { it to distanceKm(lat, lon, it.latitude, it.longitude) }
            .filter { it.second <= maxKm }
            .minByOrNull { it.second }?.first

    fun nearestCity(lat: Double, lon: Double): City =
        ukCities.minBy { distanceKm(lat, lon, it.latitude, it.longitude) }

    /** Haversine distance in km. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
