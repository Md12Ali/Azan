package com.md12ali.sakinah

import com.md12ali.sakinah.core.location.*
private fun ok(n: String, b: Boolean) = org.junit.Assert.assertTrue(n, b)
class NearbyMosquesTest {
 @org.junit.Test fun parsesAndMergesMosqueDirectories() {
  val osm = """{"version":0.6,"elements":[
    {"type":"node","id":101,"lat":51.5176,"lon":-0.0652,"tags":{"amenity":"place_of_worship","religion":"muslim","name":"East London Mosque","addr:street":"Whitechapel Road","addr:housenumber":"82-92","website":"https://www.eastlondonmosque.org.uk/"}},
    {"type":"way","id":202,"center":{"lat":51.5300,"lon":-0.0500},"tags":{"amenity":"place_of_worship","religion":"muslim","name":"Masjid Example"}},
    {"type":"node","id":303,"lat":51.5190,"lon":-0.0700,"tags":{"amenity":"place_of_worship","religion":"muslim"}}]}"""
  val o = NearbyMosqueParsers.parseOverpass(osm, 51.5180, -0.0660)
  ok("OSM 3 mosques sorted by distance", o.size == 3 && o[0].name == "East London Mosque" && o[0].distanceKm < 0.1)
  ok("OSM way centre + unnamed", o.any { it.name == "Masjid Example" } && o.any { it.name == "Mosque" })
  ok("OSM address + website", o[0].address == "82-92 Whitechapel Road" && o[0].website != null)
  val mw = """[{"uuid":"abc-1","name":"Masjid Al-Noor","slug":"masjid-al-noor-london","latitude":51.5191,"longitude":-0.0701,"localisation":"1 Some St, London","times":["06:10","12:00","14:05","16:02","17:40"],"iqama":["+15","+10","+10","+5","+10"],"proximity":120}]"""
  val m = NearbyMosqueParsers.parseMawaqitSearch(mw, 51.5180, -0.0660)
  ok("Mawaqit parsed with slug + times", m.size == 1 && m[0].mawaqitSlug == "masjid-al-noor-london" && m[0].todayTimes.size == 5)
  val merged = NearbyMosqueParsers.merge(m, o)
  ok("Merge: unnamed OSM node folded into Mawaqit twin (3 total)", merged.size == 3 && merged.count { it.mawaqitSlug != null } == 1)
  ok("Merge keeps Mawaqit name", merged.any { it.name == "Masjid Al-Noor" } && merged.none { it.name == "Mosque" })
  val e = merged[0].encode(); ok("Encode/decode round trip", NearbyMosque.decode(e)?.name == merged[0].name)
  ok("Wrapped {data:[...]} also works", NearbyMosqueParsers.parseMawaqitSearch("{\"data\":$mw}", 51.5, -0.06).size == 1)
  ok("Garbage -> empty", NearbyMosqueParsers.parseOverpass("<html>busy</html>", 0.0, 0.0).isEmpty())
}
}
