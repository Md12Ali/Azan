package com.md12ali.sakinah

import com.md12ali.sakinah.core.prayer.*
import com.md12ali.sakinah.core.timetable.*
import com.md12ali.sakinah.core.location.*
import java.time.*
private fun ok(n: String, b: Boolean) = org.junit.Assert.assertTrue(n, b)
private fun t(h: Int, m: Int) = LocalTime.of(h, m)
class MosqueParsersTest {
 @org.junit.Test fun realMosquePageFormats() {
  val d = LocalDate.of(2026, 11, 23)
  // East London Mosque year table (row format copied from the live page)
  val elm = "<table><tr><th>Date</th><th>Sunrise</th></tr><tr><td>01/01/2026</td><td>12</td><td>Rajab</td><td>1447</td><td>8:03</td><td>6:26</td><td>6:46</td><td>12:09</td><td>12:45</td><td>1:46</td><td>2:17</td><td>2:45</td><td>4:05</td><td>4:12</td><td>5:42</td><td>7:30</td></tr>" +
            "<tr><td>02/01/2026</td><td>13</td><td>Rajab</td><td>1447</td><td>8:03</td><td>6:26</td><td>6:46</td><td>12:10</td><td>1:00</td><td>1:47</td><td>2:18</td><td>2:45</td><td>4:06</td><td>4:13</td><td>5:43</td><td>7:30</td></tr></table>"
  val e = OnlineTimetableParsers.parseDatedTable(elm, "elm", hanafiAsr = true)
  ok("ELM 2 rows", e.size == 2)
  ok("ELM fajr 06:26/06:46, sunrise 08:03", e[0].begins[Prayer.FAJR] == t(6,26) && e[0].jamaat[Prayer.FAJR] == t(6,46) && e[0].begins[Prayer.SUNRISE] == t(8,3))
  ok("ELM zuhr jamaat 1:00 -> 13:00", e[1].jamaat[Prayer.DHUHR] == t(13,0))
  ok("ELM Hanafi asr mithl2 14:17", e[0].begins[Prayer.ASR] == t(14,17))
  ok("ELM Shafi asr mithl1 13:46", OnlineTimetableParsers.parseDatedTable(elm, "elm", false)[0].begins[Prayer.ASR] == t(13,46))
  ok("ELM isha 17:42 / 19:30", e[0].begins[Prayer.ISHA] == t(17,42) && e[0].jamaat[Prayer.ISHA] == t(19,30))
  // Birmingham Central Mosque monthly table
  val bcm = "<tr><td>23rd November 2026</td><td>Mon</td><td>6:13 AM</td><td>6:45 AM</td><td>7:43 AM</td><td>11:54AM</td><td>11:59 AM</td><td>1:15 PM</td><td>2:17 PM</td><td>2:45 PM</td><td>4:04 PM</td><td>4:07 PM</td><td>5:42 PM</td><td>7:15 PM</td></tr>" +
            "<tr><td>24th November 2026</td><td>Tue</td><td>6:15 AM</td><td>6:45 AM</td><td>7:44 AM</td><td>11:54AM</td><td>11:59 AM</td><td>1:15 PM</td><td>2:16 PM</td><td>2:45 PM</td><td>4:03 PM</td><td>4:06 PM</td><td>5:41 PM</td><td>7:15 PM</td></tr>"
  val b = OnlineTimetableParsers.parseDatedTable(bcm, "bcm")
  ok("BCM 2 rows dated 23/24 Nov", b.size == 2 && b[0].date == d)
  ok("BCM zuhr 11:59 (not zawal 11:54) / 13:15", b[0].begins[Prayer.DHUHR] == t(11,59) && b[0].jamaat[Prayer.DHUHR] == t(13,15))
  ok("BCM isha 17:42/19:15, sunrise 07:43", b[0].begins[Prayer.ISHA] == t(17,42) && b[0].jamaat[Prayer.ISHA] == t(19,15) && b[0].begins[Prayer.SUNRISE] == t(7,43))
  // Leicester today box
  val lei = "<div>Prayer Times</div><table><tr><th></th><th>Begins</th><th>Jamaat</th></tr><tr><td>Fajar</td><td>6:00</td><td>7.00</td></tr><tr><td>Sunrise</td><td>7:36</td><td></td></tr><tr><td>Zuhr</td><td>11:54</td><td>1.15</td></tr><tr><td>Asr</td><td>2:17</td><td>3.00</td></tr><tr><td>Magrib</td><td>4:04</td><td>4:04</td></tr><tr><td>Isha</td><td>5:40</td><td>6.30</td></tr></table>"
  val l = OnlineTimetableParsers.parseTodayWidget(lei, d)!!
  ok("Leicester fajr 6:00/7:00, zuhr J 13:15, isha 17:40/18:30", l.begins[Prayer.FAJR]==t(6,0) && l.jamaat[Prayer.FAJR]==t(7,0) && l.jamaat[Prayer.DHUHR]==t(13,15) && l.begins[Prayer.ISHA]==t(17,40) && l.jamaat[Prayer.ISHA]==t(18,30))
  // Manchester today box (Maghrib has only a Jamaat time)
  val man = "<nav>Home Fajr Classes</nav><h3>Prayer Times</h3><p>23 November 13 JUMADA AL UKHRA</p><p>Fajr</p><p>Start: 5.43</p><p>Jamaat: 7.00</p><p>Zuhr</p><p>Start: 11.55</p><p>Jamaat: 1.00</p><p>Asr</p><p>Start: 2.13</p><p>Jamaat: 2.30</p><p>Maghrib</p><p>Jamaat: 4.04</p><p>Isha</p><p>Start: 6.07</p><p>Jamaat: 7.00</p>"
  val m = OnlineTimetableParsers.parseTodayWidget(man, d)!!
  ok("Manchester maghrib 16:04 only, isha 18:07/19:00", m.begins[Prayer.MAGHRIB]==t(16,4) && m.jamaat[Prayer.MAGHRIB]==t(16,4) && m.begins[Prayer.ISHA]==t(18,7) && m.jamaat[Prayer.ISHA]==t(19,0))
  // Birmingham home box with AM/PM
  val bh = "<h4>Fajr</h4><span>6:12 AM</span><span>6:30 AM</span><h4>Sunrise</h4><span>7:41 AM</span><h4>Zuhr</h4><span>11:58 AM</span><span>1:15 PM</span><h4>Asr</h4><span>2:18 PM</span><span>2:45 PM</span><h4>Maghrib</h4><span>4:05 PM</span><span>4:08 PM</span><h4>Isha</h4><span>5:43 PM</span><span>7:15 PM</span>"
  val bb = OnlineTimetableParsers.parseTodayWidget(bh, d)!!
  ok("Birmingham box zuhr 11:58/13:15", bb.begins[Prayer.DHUHR]==t(11,58) && bb.jamaat[Prayer.DHUHR]==t(13,15))
  // Masjidbox-style with countdown noise
  val mb = "<div>05:28:21 until Fajr</div><div>Fajr</div><span>Athan</span><b>5:54</b><b>6:45</b><div>Shuruq</div><b>7:35</b><div>Dhuhr</div>Athan 11:53 <b>13:00</b> Asr Athan 14:14 14:45 Maghrib Athan 16:04 16:04 Isha Athan 17:39 18:45"
  val x = OnlineTimetableParsers.parseMasjidbox(mb, d)!!
  ok("Masjidbox skips countdown: fajr 05:54/06:45", x.begins[Prayer.FAJR]==t(5,54) && x.jamaat[Prayer.FAJR]==t(6,45))
  ok("Empty Glasgow-style widget -> null", OnlineTimetableParsers.parseTodayWidget("<th>FAJR</th><th>SUNRISE</th><th>ZUHR</th><td>Begins</td><td></td>", d) == null)
  ok("London GPS -> East London Mosque", Places.nearestCentralMosque(51.52, -0.07)?.name == "East London Mosque")
  ok("Birmingham GPS -> Birmingham Central Mosque", Places.nearestCentralMosque(52.48, -1.90)?.name == "Birmingham Central Mosque")
}
}
