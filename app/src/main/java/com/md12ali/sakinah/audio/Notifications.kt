package com.md12ali.sakinah.audio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.md12ali.sakinah.R
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.ui.MainActivity

object Notifications {
    const val CH_ADHAN = "adhan_playback"   // silent channel – the service itself plays the Adhan
    const val CH_GENTLE = "prayer_gentle"   // default soft notification sound

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ADHAN, "Adhan", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shown while the Adhan is playing"
            setSound(null, null)
            enableVibration(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_GENTLE, "Gentle prayer reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Soft notification at prayer time (no Adhan)"
        })
    }

    fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun showGentle(context: Context, prayer: Prayer, time: String, place: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33) return
        val n = NotificationCompat.Builder(context, CH_GENTLE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${prayer.english} · ${prayer.arabic}")
            .setContentText(listOf(time, place).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "It's time for prayer" })
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(2000 + prayer.ordinal, n)
    }
}
