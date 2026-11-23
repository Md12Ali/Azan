package com.md12ali.sakinah.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.md12ali.sakinah.SakinahApp
import com.md12ali.sakinah.audio.AdhanPlaybackService
import com.md12ali.sakinah.audio.Notifications
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.data.settings.AlertMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Fires at prayer time. Exact alarms are an allowed reason to start a foreground service from the background. */
class AdhanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra(AdhanScheduler.EXTRA_PRAYER)?.let { runCatching { Prayer.valueOf(it) }.getOrNull() } ?: return
        val time = intent.getStringExtra(AdhanScheduler.EXTRA_TIME).orEmpty()
        val place = intent.getStringExtra(AdhanScheduler.EXTRA_PLACE).orEmpty()
        val pending = goAsync()
        receiverScope.launch {
            try {
                val app = context.applicationContext as SakinahApp
                val s = app.container.settings.current()
                when (s.alerts[prayer] ?: AlertMode.ADHAN) {
                    AlertMode.ADHAN, AlertMode.SOFT_ADHAN -> {
                        val svc = Intent(context, AdhanPlaybackService::class.java)
                            .putExtra(AdhanScheduler.EXTRA_PRAYER, prayer.name)
                            .putExtra(AdhanScheduler.EXTRA_TIME, time)
                            .putExtra(AdhanScheduler.EXTRA_PLACE, place)
                        try {
                            ContextCompat.startForegroundService(context, svc)
                        } catch (e: Exception) {
                            // e.g. ForegroundServiceStartNotAllowedException – never stay silent: ring the alarm notification.
                            android.util.Log.e("Adhan", "Could not start Adhan player", e)
                            Notifications.showFallbackAlarm(context, prayer, time, place)
                        }
                    }
                    AlertMode.GENTLE -> Notifications.showGentle(context, prayer, time, place)
                    AlertMode.SILENT -> Unit
                }
                AdhanScheduler.rescheduleAll(context)   // keep the rolling window topped up
            } finally {
                pending.finish()
            }
        }
    }
}

/** Reboot / app update / clock or timezone change / exact-alarm permission granted → reschedule only. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        receiverScope.launch {
            try { AdhanScheduler.rescheduleAll(context) } finally { pending.finish() }
        }
    }
}
