package com.md12ali.sakinah.core.location

import com.md12ali.sakinah.core.timetable.MiniJsonAccess

/**
 * A mosque found near the user, anywhere in the world.
 * @param mawaqitSlug set when the mosque publishes its own timetable on Mawaqit (begin + jamaat times).
 * @param todayTimes  today's begin times from the Mawaqit search result (fajr, sunrise?, dhuhr, asr, maghrib, isha).
 */
data class NearbyMosque(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val website: String? = null,
    val mawaqitSlug: String? = null,
    val todayTimes: List<String> = emptyList(),
    val distanceKm: Double = 0.0,
    val source: String = "osm"          // "mawaqit" | "osm" | "uk-central"
) {
    val hasTimetable: Boolean get() = mawaqitSlug != null || website != null

    /** Compact one-line encoding for settings storage. */
    fun encode(): String = listOf(id, name, latitude, longitude, address.orEmpty(), website.orEmpty(), mawaqitSlug.orEmpty(), source)
        .joinToString("\u001F") { it.toString().replace("\u001F", " ").replace("\n", " ") }

    companion object {
        fun decode(s: String?): NearbyMosque? {
            val p = s?.split("\u001F") ?: return null
            if (p.size < 8) return null
            return NearbyMosque(
                id = p[0], name = p[1], latitude = p[2].toDoubleOrNull() ?: return null, longitude = p[3].toDoubleOrNull() ?: return null,
                address = p[4].ifBlank { null }, website = p[5].ifBlank { null }, mawaqitSlug = p[6].ifBlank { null }, source = p[7]
            )
        }
    }
}

/** Pure-Kotlin parsers for the two worldwide mosque directories (unit-tested, no Android types). */
object NearbyMosqueParsers {

    /**
     * OpenStreetMap Overpass JSON: elements with tags amenity=place_of_worship + religion=muslim.
     * Nodes carry lat/lon; ways/relations carry "center" (we request `out center`).
     */
    fun parseOverpass(json: String, fromLat: Double, fromLon: Double): List<NearbyMosque> {
        val root = MiniJsonAccess.parse(json) as? Map<*, *> ?: return emptyList()
        val elements = root["elements"] as? List<*> ?: return emptyList()
        return elements.mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            val center = m["center"] as? Map<*, *>
            val lat = num(m["lat"]) ?: num(center?.get("lat")) ?: return@mapNotNull null
            val lon = num(m["lon"]) ?: num(center?.get("lon")) ?: return@mapNotNull null
            val tags = m["tags"] as? Map<*, *> ?: emptyMap<String, Any>()
            fun t(k: String) = (tags[k] as? String)?.takeIf { it.isNotBlank() }
            val name = t("name:en") ?: t("name") ?: t("official_name") ?: t("alt_name") ?: "Mosque"
            val address = listOfNotNull(
                listOfNotNull(t("addr:housenumber"), t("addr:street")).joinToString(" ").ifBlank { null },
                t("addr:city") ?: t("addr:suburb"), t("addr:postcode")
            ).joinToString(", ").ifBlank { null }
            NearbyMosque(
                id = "osm_${m["type"]}_${m["id"]}", name = name, latitude = lat, longitude = lon,
                address = address, website = t("website") ?: t("contact:website") ?: t("url"),
                distanceKm = Places.distanceKm(fromLat, fromLon, lat, lon), source = "osm"
            )
        }.sortedBy { it.distanceKm }
    }

    /**
     * Mawaqit search (mawaqit.net/api/2.0/mosque/search?lat=&lon=). The result is a JSON array of mosques.
     * Field names are read tolerantly (the API is undocumented): name|label, slug, latitude|lat, longitude|lng|lon,
     * localisation|address, times (5 or 6 "HH:mm"), url.
     */
    fun parseMawaqitSearch(json: String, fromLat: Double, fromLon: Double): List<NearbyMosque> {
        val root = MiniJsonAccess.parse(json)
        val list = (root as? List<*>) ?: ((root as? Map<*, *>)?.let { it["data"] ?: it["mosques"] ?: it["results"] } as? List<*>) ?: return emptyList()
        return list.mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            fun s(vararg k: String) = k.firstNotNullOfOrNull { (m[it] as? String)?.takeIf { v -> v.isNotBlank() } }
            val lat = num(m["latitude"]) ?: num(m["lat"]) ?: return@mapNotNull null
            val lon = num(m["longitude"]) ?: num(m["lng"]) ?: num(m["lon"]) ?: return@mapNotNull null
            val slug = s("slug") ?: s("url")?.substringAfterLast('/')?.substringBefore('?')
            val times = (m["times"] as? List<*>)?.mapNotNull { (it as? String)?.takeIf { v -> Regex("""\d{1,2}:\d{2}""").matches(v) } }.orEmpty()
            NearbyMosque(
                id = "mawaqit_${s("uuid") ?: slug ?: "$lat,$lon"}",
                name = s("name", "label") ?: "Mosque",
                latitude = lat, longitude = lon,
                address = s("localisation", "address", "associationName"),
                website = s("site", "website"),
                mawaqitSlug = slug,
                todayTimes = times,
                distanceKm = Places.distanceKm(fromLat, fromLon, lat, lon),
                source = "mawaqit"
            )
        }.sortedBy { it.distanceKm }
    }

    /**
     * Merge both directories: a Mawaqit mosque within 150 m of an OSM mosque is the same building – keep one entry
     * with the Mawaqit timetable and the better name. Also folds in the curated UK central mosques.
     */
    fun merge(mawaqit: List<NearbyMosque>, osm: List<NearbyMosque>, central: List<NearbyMosque> = emptyList()): List<NearbyMosque> {
        val out = mutableListOf<NearbyMosque>()
        (central + mawaqit).forEach { a -> if (out.none { Places.distanceKm(it.latitude, it.longitude, a.latitude, a.longitude) < 0.15 }) out += a }
        osm.forEach { o ->
            val twin = out.indexOfFirst { Places.distanceKm(it.latitude, it.longitude, o.latitude, o.longitude) < 0.15 }
            if (twin >= 0) {
                val a = out[twin]
                out[twin] = a.copy(
                    name = if (a.name == "Mosque") o.name else a.name,
                    address = a.address ?: o.address,
                    website = a.website ?: o.website
                )
            } else out += o
        }
        return out.sortedBy { it.distanceKm }
    }

    /** Overpass QL for mosques around a point. */
    fun overpassQuery(lat: Double, lon: Double, radiusM: Int): String =
        "[out:json][timeout:20];(nwr[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"](around:$radiusM,$lat,$lon);" +
            "nwr[\"building\"=\"mosque\"](around:$radiusM,$lat,$lon););out center tags 80;"

    private fun num(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }
}
