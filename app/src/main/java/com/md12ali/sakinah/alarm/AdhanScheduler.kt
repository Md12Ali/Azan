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
import com.md12ali.sakinah.ui.MainActivity
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Schedules one alarm per prayer for today + tomorrow (rolling window, refreshed after every Adhan,
 * daily by WorkManager, on app open and after reboot / clock or timezone change).
 *
 * Uses AlarmManager.setAlarmClock(): the alarm type Android and phone makers (Samsung, Xiaomi, Oppo…)
 * treat as highest priority – it fires on time even in deep Doze / battery saver, like the Clock app.
 */
object AdhanScheduler {
    private const val TAG = "AdhanScheduler"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_TIME = "time"
    const val EXTRA_PLACE = "place"
    const val EXTRA_MODE = "mode"

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    private fun showIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 7, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun set(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        try {
            if (canScheduleExact(context)) am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(context)), pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext as SakinahApp
        val c = app.container
        val settings = c.settings.current()
        val am = context.getSystemService(AlarmManager::class.java)
        val today = LocalDate.now()
        val now = ZonedDateTime.now()

        // Cancel the window we manage (yesterday..+2) so changed times/settings never leave stale alarms.
        for (offset in -1..2) for (p in Prayer.adhanPrayers) am.cancel(pendingIntent(context, today.plusDays(offset.toLong()), p, null, null, null))

        var first: Pair<Prayer, ZonedDateTime>? = null
        for (offset in 0..1) {
            val date = today.plusDays(offset.toLong())
            val (day, place) = c.prayerRepository.day(date)
            for (p in Prayer.adhanPrayers) {
                val mode = settings.alerts[p] ?: AlertMode.ADHAN
                if (mode == AlertMode.SILENT) continue
                val time = (if (settings.alertAtJamaat) day.jamaat[p] else null) ?: day.begins[p] ?: continue
                if (!time.isAfter(now)) continue
                set(context, time.toInstant().toEpochMilli(), pendingIntent(context, date, p, time, place.title, mode))
                if (first == null || time.isBefore(first.second)) first = p to time
                Log.d(TAG, "Scheduled ${p.english} at $time (${place.title})")
            }
        }
        first?.let { (p, t) ->
            AdhanLog.setNext(context, "${p.english} at ${t.format(DateTimeFormatter.ofPattern("EEE HH:mm"))}" +
                if (canScheduleExact(context)) "" else " (may be late – exact alarms not allowed)")
        }
    }

    /** A real Adhan alarm [seconds] from now – checks sound, lock screen and battery settings end-to-end. */
    fun scheduleTest(context: Context, seconds: Long = 60) {
        val at = System.currentTimeMillis() + seconds * 1000
        val time = java.time.LocalTime.now().plusSeconds(seconds).withSecond(0).withNano(0)
        val intent = Intent(context, AdhanAlarmReceiver::class.java).apply {
            action = "com.md12ali.sakinah.ADHAN_TEST"
            putExtra(EXTRA_PRAYER, Prayer.DHUHR.name)
            putExtra(EXTRA_TIME, time.toString())
            putExtra(EXTRA_PLACE, "Test Adhan")
            putExtra(EXTRA_MODE, AlertMode.ADHAN.name)
        }
        val pi = PendingIntent.getBroadcast(context, 9999, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        set(context, at, pi)
        AdhanLog.add(context, "Test Adhan scheduled for ${time}")
    }

    private fun pendingIntent(context: Context, date: LocalDate, p: Prayer, time: ZonedDateTime?, place: String?, mode: AlertMode?): PendingIntent {
        val intent = Intent(context, AdhanAlarmReceiver::class.java).apply {
            action = "com.md12ali.sakinah.ADHAN_${date}_${p.name}"   // unique action → unique PendingIntent
            putExtra(EXTRA_PRAYER, p.name)
            time?.let { putExtra(EXTRA_TIME, it.toLocalTime().toString()) }
            place?.let { putExtra(EXTRA_PLACE, it) }
            mode?.let { putExtra(EXTRA_MODE, it.name) }
        }
        val requestCode = (date.dayOfYear * 10) + p.ordinal
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
