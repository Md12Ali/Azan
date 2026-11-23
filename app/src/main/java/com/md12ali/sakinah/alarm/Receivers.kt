package com.md12ali.sakinah.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.md12ali.sakinah.audio.AdhanPlaybackService
import com.md12ali.sakinah.audio.Notifications
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.data.settings.AlertMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Fires at prayer time. The player is started IMMEDIATELY (synchronously) – Android only allows a
 * background app to start a foreground service for a few seconds after an alarm, so nothing slow
 * (database, network) happens before it.
 */
class AdhanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra(AdhanScheduler.EXTRA_PRAYER)?.let { runCatching { Prayer.valueOf(it) }.getOrNull() } ?: return
        val time = intent.getStringExtra(AdhanScheduler.EXTRA_TIME).orEmpty()
        val place = intent.getStringExtra(AdhanScheduler.EXTRA_PLACE).orEmpty()
        val mode = intent.getStringExtra(AdhanScheduler.EXTRA_MODE)?.let { runCatching { AlertMode.valueOf(it) }.getOrNull() } ?: AlertMode.ADHAN
        AdhanLog.add(context, "Alarm fired: ${prayer.english} $time (${mode.label})")

        when (mode) {
            AlertMode.ADHAN, AlertMode.SOFT_ADHAN -> {
                val svc = Intent(context, AdhanPlaybackService::class.java)
                    .putExtra(AdhanScheduler.EXTRA_PRAYER, prayer.name)
                    .putExtra(AdhanScheduler.EXTRA_TIME, time)
                    .putExtra(AdhanScheduler.EXTRA_PLACE, place)
                    .putExtra(AdhanScheduler.EXTRA_MODE, mode.name)
                try {
                    ContextCompat.startForegroundService(context, svc)
                } catch (e: Exception) {
                    // Never stay silent: ring the loud alarm notification instead.
                    AdhanLog.add(context, "Player could not start: ${e.javaClass.simpleName} – used alarm notification")
                    Notifications.showFallbackAlarm(context, prayer, time, place)
                }
            }
            AlertMode.GENTLE -> Notifications.showGentle(context, prayer, time, place)
            AlertMode.SILENT -> Unit
        }

        // Afterwards (slow work is fine now): top up the rolling window of alarms.
        val pending = goAsync()
        receiverScope.launch {
            try { AdhanScheduler.rescheduleAll(context) } catch (_: Exception) {} finally { pending.finish() }
        }
    }
}

/** Reboot / app update / clock or timezone change / exact-alarm permission granted → reschedule only. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        receiverScope.launch {
            try { AdhanScheduler.rescheduleAll(context) } catch (_: Exception) {} finally { pending.finish() }
        }
    }
}
