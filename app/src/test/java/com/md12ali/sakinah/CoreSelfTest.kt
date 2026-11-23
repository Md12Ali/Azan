package com.md12ali.sakinah

import com.md12ali.sakinah.core.prayer.*
import com.md12ali.sakinah.core.timetable.*
import com.md12ali.sakinah.core.location.*
import java.time.*
fun check(name: String, ok: Boolean) = org.junit.Assert.assertTrue(name, ok)
fun near(a: ZonedDateTime, hm: String) = Math.abs(Duration.between(a.toLocalTime(), LocalTime.parse(hm)).toMinutes()) <= 1
class CoreSelfTest {
 @org.junit.Test fun prayerTimesAndParsers() {
  val z = ZoneId.of("Europe/London")
  val w = PrayerCalculator.calculate(LocalDate.of(2026,11,23), 51.8838, -0.4339, z).begins
  val aw = mapOf(Prayer.FAJR to "05:55", Prayer.SUNRISE to "07:34", Prayer.DHUHR to "11:48", Prayer.ASR to "14:15", Prayer.MAGHRIB to "16:01", Prayer.ISHA to "17:33")
  aw.forEach { (p, t) -> check("Winter $p vs AlAdhan $t (${w[p]!!.toLocalTime()})", near(w[p]!!, t)) }
  val s = PrayerCalculator.calculate(LocalDate.of(2026,6,21), 51.8838, -0.4339, z).begins
  val asum = mapOf(Prayer.FAJR to "02:42", Prayer.SUNRISE to "04:42", Prayer.DHUHR to "13:04", Prayer.ASR to "18:42", Prayer.MAGHRIB to "21:25", Prayer.ISHA to "22:46")
  asum.forEach { (p, t) -> check("Summer $p vs AlAdhan $t (${s[p]!!.toLocalTime()})", near(s[p]!!, t)) }
  val withOff = PrayerCalculator.calculate(LocalDate.of(2026,11,23), 51.8838, -0.4339, z, CalcParams(offsets = UK_MOSQUE_OFFSETS)).begins
  check("UK offsets Dhuhr 11:53 like Luton Masjidbox (${withOff[Prayer.DHUHR]!!.toLocalTime()})", near(withOff[Prayer.DHUHR]!!, "11:53"))
  // every day of a year produces ordered times (no NaN) for Edinburgh & Luton
  for (lat in listOf(51.88, 55.95, 58.97)) { var d = LocalDate.of(2026,1,1); while (d.year==2026) { val b = PrayerCalculator.calculate(d, lat, -2.0, z).begins; val o = Prayer.entries.map { b[it]!! }; check2(o.zipWithNext().all { it.first < it.second }, "$lat $d $o"); d = d.plusDays(1) } }
  println("PASS full-year ordering at 51.9/55.9/59.0N")
  val csv = "# comment\ndate,fajr,fajr_jamaat,sunrise,dhuhr,dhuhr_jamaat,asr,asr_jamaat,maghrib,maghrib_jamaat,isha,isha_jamaat\n23/11/2026,5:54,6:45,7:35,11:53,1:00,2:14,2:45,4:04,4:04,5:39,6:45\n"
  val r = TimetableCsv.parse(csv).single()
  check("CSV 12h → Dhuhr jamaat 13:00", r.jamaat[Prayer.DHUHR] == LocalTime.of(13,0))
  check("CSV 12h → Isha 17:39", r.begins[Prayer.ISHA] == LocalTime.of(17,39))
  val mb = "<html><div>05:28:21 until Fajr</div><div>Fajr</div><span>Athan</span><b>5:54</b><b>6:45</b><div>Shuruq</div><b>7:35</b><div>Dhuhr</div>Athan 11:53 <b>13:00</b> Asr Athan 14:14 14:45 Maghrib Athan 16:04 16:04 Isha Athan 17:39 18:45</html>"
  val m = OnlineTimetableParsers.parseMasjidbox(mb, LocalDate.of(2026,11,23))!!
  check("Masjidbox Fajr 05:54/06:45", m.begins[Prayer.FAJR] == LocalTime.of(5,54) && m.jamaat[Prayer.FAJR] == LocalTime.of(6,45))
  check("Masjidbox Isha 17:39/18:45 & sunrise", m.begins[Prayer.ISHA] == LocalTime.of(17,39) && m.jamaat[Prayer.ISHA] == LocalTime.of(18,45) && m.begins[Prayer.SUNRISE]==LocalTime.of(7,35))
  val mw = """<script>var confData = {"name":"X","calendar":[{"1":["06:10","07:50","12:10","14:05","16:20","17:55"],"2":["06:11","07:51","12:10","14:04","16:19","17:54"]},{}],"iqamaCalendar":[{"1":["+20","+10","14:30","+5","+15"]}],"x":true};</script>"""
  val mr = OnlineTimetableParsers.parseMawaqit(mw, 2026)
  check("Mawaqit 2 days parsed", mr.size == 2)
  check("Mawaqit iqama +20 → 06:30, fixed 14:30", mr[0].jamaat[Prayer.FAJR] == LocalTime.of(6,30) && mr[0].jamaat[Prayer.ASR] == LocalTime.of(14,30))
  val links = OnlineTimetableParsers.discoverLinks("""<a href="https://masjidbox.com/prayer-times/luton-central-mosque">x</a> <a href="https://mawaqit.net/en/my-mosque-london">""")
  check("Discover links $links", links == listOf("masjidbox:luton-central-mosque", "mawaqit:my-mosque-london"))
  check("Nearest mosque from Bury Park = Luton", Places.nearestCentralMosque(51.885, -0.44)?.city == "Luton")
  check("Nearest from Wembley = London", Places.nearestCentralMosque(51.556, -0.279)?.city == "London")
}
}
fun check2(ok: Boolean, msg: String) = org.junit.Assert.assertTrue("ordering $msg", ok)
