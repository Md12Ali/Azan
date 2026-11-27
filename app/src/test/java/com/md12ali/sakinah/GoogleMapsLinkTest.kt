package com.md12ali.sakinah

import com.md12ali.sakinah.core.location.GoogleMapsLink
private fun okG(n: String, b: Boolean) = org.junit.Assert.assertTrue(n, b)
class GoogleMapsLinkTest {
 @org.junit.Test fun readsGoogleMapsLinks() {
  val a = GoogleMapsLink.parse("https://www.google.com/maps/place/East+London+Mosque/@51.5180,-0.0660,17z/data=!3m1!4b1!4m6!3m5!1s0x48761cc!8m2!3d51.5176!4d-0.0652!16s")!!
  okG("pin preferred + name", a.latitude == 51.5176 && a.longitude == -0.0652 && a.name == "East London Mosque")
  val b = GoogleMapsLink.parse("https://maps.google.com/?q=23.8103,90.4125")!!
  okG("q= coords", b.latitude == 23.8103 && b.name == null)
  val c = GoogleMapsLink.parse("https://www.google.com/maps/place/Masjid+al-Hara%C3%A1m/@21.4225,39.8262,15z")!!
  okG("@ centre + url-decoded name", c.latitude == 21.4225 && c.name!!.startsWith("Masjid al-Hara"))
  okG("first url from shared text", GoogleMapsLink.firstUrl("Jamia Masjid Luton\nhttps://maps.app.goo.gl/AbC123") == "https://maps.app.goo.gl/AbC123")
  okG("name from shared text", GoogleMapsLink.nameFromSharedText("Jamia Masjid Luton\nhttps://maps.app.goo.gl/AbC123") == "Jamia Masjid Luton")
  okG("is google maps short link", GoogleMapsLink.isGoogleMaps("https://maps.app.goo.gl/AbC123"))
  okG("rejects non-coords", GoogleMapsLink.parse("https://example.com/page") == null)
}
}
