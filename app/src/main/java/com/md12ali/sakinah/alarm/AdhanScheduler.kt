package com.md12ali.sakinah.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.md12ali.sakinah.SakinahApp
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.data.settings.AlertMode
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Schedules one exact alarm per prayer for today + tomorrow.
 * Every time an alarm fires (and daily via WorkManager, and after reboot) we reschedule, so there is
 * always a rolling 24–48 h window of alarms – even if the app isn't opened for weeks.
 */
object AdhanScheduler {
    private const val TAG = "AdhanScheduler"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_TIME = "time"
    const val EXTRA_PLACE = "place"

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext as SakinahApp
        val c = app.container
        val settings = c.settings.current()
        val am = context.getSystemService(AlarmManager::class.java)
        val today = LocalDate.now()
        val now = ZonedDateTime.now()

        // Cancel the window we manage (yesterday..+2) so changed times/settings never leave stale alarms.
        for (offset in -1..2) for (p in Prayer.adhanPrayers) am.cancel(pendingIntent(context, today.plusDays(offset.toLong()), p, null, null))

        for (offset in 0..1) {
            val date = today.plusDays(offset.toLong())
            val (day, place) = c.prayerRepository.day(date)
            for (p in Prayer.adhanPrayers) {
                val mode = settings.alerts[p] ?: AlertMode.ADHAN
                if (mode == AlertMode.SILENT) continue
                val time = (if (settings.alertAtJamaat) day.jamaat[p] else null) ?: day.begins[p] ?: continue
                if (!time.isAfter(now)) continue
                val pi = pendingIntent(context, date, p, time, place.title)
                val at = time.toInstant().toEpochMilli()
                if (canScheduleExact(context)) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    // Without "Alarms & reminders" access Android may delay this by a few minutes.
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
                Log.d(TAG, "Scheduled ${p.english} at $time (${place.title}, exact=${canScheduleExact(context)})")
            }
        }
    }

    /** Schedules a real Adhan alarm [seconds] from now – lets the user check sound, lock screen and battery settings. */
    fun scheduleTest(context: Context, seconds: Long = 60) {
        val am = context.getSystemService(AlarmManager::class.java)
        val at = System.currentTimeMillis() + seconds * 1000
        val time = java.time.LocalTime.now().plusSeconds(seconds).withSecond(0).withNano(0)
        val intent = Intent(context, AdhanAlarmReceiver::class.java).apply {
            action = "com.md12ali.sakinah.ADHAN_TEST"
            putExtra(EXTRA_PRAYER, Prayer.DHUHR.name)
            putExtra(EXTRA_TIME, time.toString())
            putExtra(EXTRA_PLACE, "Test Adhan")
        }
        val pi = PendingIntent.getBroadcast(context, 9999, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun pendingIntent(context: Context, date: LocalDate, p: Prayer, time: ZonedDateTime?, place: String?): PendingIntent {
        val intent = Intent(context, AdhanAlarmReceiver::class.java).apply {
            action = "com.md12ali.sakinah.ADHAN_${date}_${p.name}"   // unique action → unique PendingIntent
            putExtra(EXTRA_PRAYER, p.name)
            time?.let { putExtra(EXTRA_TIME, it.toLocalTime().toString()) }
            place?.let { putExtra(EXTRA_PLACE, it) }
        }
        val requestCode = (date.dayOfYear * 10) + p.ordinal
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
