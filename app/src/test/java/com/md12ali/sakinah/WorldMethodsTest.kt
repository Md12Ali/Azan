package com.md12ali.sakinah

import com.md12ali.sakinah.core.prayer.*
import java.time.*
class WorldMethodsTest {
 @org.junit.Test fun matchesAlAdhanWorldwide() {
  data class C(val n: String, val lat: Double, val lon: Double, val tz: String, val m: CalculationMethod, val h: Madhab, val exp: List<String>)
  val d = LocalDate.of(2026,11,24)
  val cs = listOf(
    C("Makkah",21.4225,39.8262,"Asia/Riyadh",CalculationMethod.UMM_AL_QURA,Madhab.SHAFI, listOf("05:17","06:37","12:07","15:16","17:37","19:07")),
    C("Cairo",30.0444,31.2357,"Africa/Cairo",CalculationMethod.EGYPT,Madhab.SHAFI, listOf("04:57","06:27","11:42","14:37","16:56","18:17")),
    C("Karachi",24.8607,67.0011,"Asia/Karachi",CalculationMethod.KARACHI,Madhab.HANAFI, listOf("05:35","06:54","12:19","16:07","17:43","19:02")),
    C("Istanbul",41.0082,28.9784,"Europe/Istanbul",CalculationMethod.TURKEY,Madhab.HANAFI, listOf("06:25","07:55","12:56","16:03","17:46","19:11")),
    C("KualaLumpur",3.139,101.6869,"Asia/Kuala_Lumpur",CalculationMethod.SINGAPORE,Madhab.SHAFI, listOf("05:39","07:01","13:00","16:24","18:59","20:12")),
    C("NewYork",40.7128,-74.006,"America/New_York",CalculationMethod.ISNA,Madhab.SHAFI, listOf("05:33","06:53","11:43","14:13","16:32","17:52")))
  for (c in cs) {
    val b = PrayerCalculator.calculate(d, c.lat, c.lon, ZoneId.of(c.tz), CalcParams(c.m, c.h)).begins
    val got = Prayer.entries.map { b[it]!!.toLocalTime().toString() }
    val diffs = got.zip(c.exp).map { (g, e) -> Duration.between(LocalTime.parse(e), LocalTime.parse(g)).toMinutes() }
    org.junit.Assert.assertTrue("${c.n} got=$got diffs=$diffs", diffs.all { kotlin.math.abs(it) <= 1 })
  }
}
}
