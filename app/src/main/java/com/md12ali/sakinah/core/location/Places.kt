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

data class City(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val countryCode: String? = "GB",
    val country: String? = "United Kingdom"
)

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

    /** UK cities (one per central mosque). */
    val ukCities: List<City> = mosques.map { City(it.city, it.latitude, it.longitude) }.distinctBy { it.name }

    /**
     * Built-in world cities (works offline, no search service needed). Coordinates are city centres.
     * Any other city can be found with the in-app worldwide search (Android Geocoder) or GPS.
     */
    val worldCities: List<City> = listOf(
        w("Makkah", 21.4225, 39.8262, "SA", "Saudi Arabia"), w("Madinah", 24.4672, 39.6111, "SA", "Saudi Arabia"),
        w("Riyadh", 24.7136, 46.6753, "SA", "Saudi Arabia"), w("Jeddah", 21.4858, 39.1925, "SA", "Saudi Arabia"),
        w("Dubai", 25.2048, 55.2708, "AE", "United Arab Emirates"), w("Abu Dhabi", 24.4539, 54.3773, "AE", "United Arab Emirates"),
        w("Doha", 25.2854, 51.5310, "QA", "Qatar"), w("Kuwait City", 29.3759, 47.9774, "KW", "Kuwait"),
        w("Manama", 26.2285, 50.5860, "BH", "Bahrain"), w("Muscat", 23.5880, 58.3829, "OM", "Oman"),
        w("Sana'a", 15.3694, 44.1910, "YE", "Yemen"), w("Amman", 31.9454, 35.9284, "JO", "Jordan"),
        w("Jerusalem", 31.7683, 35.2137, "PS", "Palestine"), w("Gaza", 31.5017, 34.4668, "PS", "Palestine"),
        w("Damascus", 33.5138, 36.2765, "SY", "Syria"), w("Beirut", 33.8938, 35.5018, "LB", "Lebanon"),
        w("Baghdad", 33.3152, 44.3661, "IQ", "Iraq"), w("Tehran", 35.6892, 51.3890, "IR", "Iran"),
        w("Cairo", 30.0444, 31.2357, "EG", "Egypt"), w("Alexandria", 31.2001, 29.9187, "EG", "Egypt"),
        w("Khartoum", 15.5007, 32.5599, "SD", "Sudan"), w("Tripoli", 32.8872, 13.1913, "LY", "Libya"),
        w("Tunis", 36.8065, 10.1815, "TN", "Tunisia"), w("Algiers", 36.7538, 3.0588, "DZ", "Algeria"),
        w("Casablanca", 33.5731, -7.5898, "MA", "Morocco"), w("Rabat", 34.0209, -6.8416, "MA", "Morocco"),
        w("Istanbul", 41.0082, 28.9784, "TR", "Türkiye"), w("Ankara", 39.9334, 32.8597, "TR", "Türkiye"),
        w("Baku", 40.4093, 49.8671, "AZ", "Azerbaijan"), w("Tashkent", 41.2995, 69.2401, "UZ", "Uzbekistan"),
        w("Almaty", 43.2220, 76.8512, "KZ", "Kazakhstan"), w("Kabul", 34.5553, 69.2075, "AF", "Afghanistan"),
        w("Karachi", 24.8607, 67.0011, "PK", "Pakistan"), w("Lahore", 31.5204, 74.3587, "PK", "Pakistan"),
        w("Islamabad", 33.6844, 73.0479, "PK", "Pakistan"), w("Mirpur (AJK)", 33.1478, 73.7518, "PK", "Pakistan"),
        w("Peshawar", 34.0151, 71.5249, "PK", "Pakistan"), w("Delhi", 28.6139, 77.2090, "IN", "India"),
        w("Mumbai", 19.0760, 72.8777, "IN", "India"), w("Hyderabad", 17.3850, 78.4867, "IN", "India"),
        w("Kolkata", 22.5726, 88.3639, "IN", "India"), w("Dhaka", 23.8103, 90.4125, "BD", "Bangladesh"),
        w("Sylhet", 24.8949, 91.8687, "BD", "Bangladesh"), w("Chittagong", 22.3569, 91.7832, "BD", "Bangladesh"),
        w("Colombo", 6.9271, 79.8612, "LK", "Sri Lanka"), w("Kathmandu", 27.7172, 85.3240, "NP", "Nepal"),
        w("Kuala Lumpur", 3.1390, 101.6869, "MY", "Malaysia"), w("Singapore", 1.3521, 103.8198, "SG", "Singapore"),
        w("Jakarta", -6.2088, 106.8456, "ID", "Indonesia"), w("Surabaya", -7.2575, 112.7521, "ID", "Indonesia"),
        w("Bandar Seri Begawan", 4.9031, 114.9398, "BN", "Brunei"), w("Manila", 14.5995, 120.9842, "PH", "Philippines"),
        w("Lagos", 6.5244, 3.3792, "NG", "Nigeria"), w("Kano", 12.0022, 8.5920, "NG", "Nigeria"),
        w("Abuja", 9.0765, 7.3986, "NG", "Nigeria"), w("Dakar", 14.7167, -17.4677, "SN", "Senegal"),
        w("Mogadishu", 2.0469, 45.3182, "SO", "Somalia"), w("Hargeisa", 9.5600, 44.0650, "SO", "Somalia"),
        w("Nairobi", -1.2921, 36.8219, "KE", "Kenya"), w("Addis Ababa", 8.9806, 38.7578, "ET", "Ethiopia"),
        w("Dar es Salaam", -6.7924, 39.2083, "TZ", "Tanzania"), w("Johannesburg", -26.2041, 28.0473, "ZA", "South Africa"),
        w("Cape Town", -33.9249, 18.4241, "ZA", "South Africa"), w("Paris", 48.8566, 2.3522, "FR", "France"),
        w("Brussels", 50.8503, 4.3517, "BE", "Belgium"), w("Amsterdam", 52.3676, 4.9041, "NL", "Netherlands"),
        w("Berlin", 52.5200, 13.4050, "DE", "Germany"), w("Frankfurt", 50.1109, 8.6821, "DE", "Germany"),
        w("Madrid", 40.4168, -3.7038, "ES", "Spain"), w("Rome", 41.9028, 12.4964, "IT", "Italy"),
        w("Vienna", 48.2082, 16.3738, "AT", "Austria"), w("Stockholm", 59.3293, 18.0686, "SE", "Sweden"),
        w("Oslo", 59.9139, 10.7522, "NO", "Norway"), w("Copenhagen", 55.6761, 12.5683, "DK", "Denmark"),
        w("Dublin", 53.3498, -6.2603, "IE", "Ireland"), w("Sarajevo", 43.8563, 18.4131, "BA", "Bosnia and Herzegovina"),
        w("Tirana", 41.3275, 19.8187, "AL", "Albania"), w("Pristina", 42.6629, 21.1655, "XK", "Kosovo"),
        w("Moscow", 55.7558, 37.6173, "RU", "Russia"), w("Kazan", 55.7887, 49.1221, "RU", "Russia"),
        w("New York", 40.7128, -74.0060, "US", "United States"), w("Chicago", 41.8781, -87.6298, "US", "United States"),
        w("Houston", 29.7604, -95.3698, "US", "United States"), w("Los Angeles", 34.0522, -118.2437, "US", "United States"),
        w("Dearborn", 42.3223, -83.1763, "US", "United States"), w("Toronto", 43.6532, -79.3832, "CA", "Canada"),
        w("Montreal", 45.5017, -73.5673, "CA", "Canada"), w("Sydney", -33.8688, 151.2093, "AU", "Australia"),
        w("Melbourne", -37.8136, 144.9631, "AU", "Australia"), w("Auckland", -36.8509, 174.7645, "NZ", "New Zealand")
    )

    private fun w(name: String, lat: Double, lon: Double, cc: String, country: String) = City(name, lat, lon, cc, country)

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
