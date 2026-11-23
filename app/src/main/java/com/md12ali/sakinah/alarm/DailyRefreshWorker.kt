package com.md12ali.sakinah.alarm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.md12ali.sakinah.SakinahApp
import com.md12ali.sakinah.data.settings.LocationMode
import java.util.concurrent.TimeUnit

/**
 * Every ~6 h: refresh GPS (Auto mode) → pick the nearest central mosque → download its timetable if the
 * cache is short → reschedule alarms. Travelling from Luton to Birmingham? The next run switches mosque.
 */
class DailyRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as SakinahApp).container
        if (c.settings.current().mode == LocationMode.AUTO_GPS) {
            c.location.currentLocation()?.let { c.settings.setLastFix(it.latitude, it.longitude) }
        }
        runCatching { c.prayerRepository.refreshTimetableIfNeeded() }
        AdhanScheduler.rescheduleAll(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<DailyRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily_refresh", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
