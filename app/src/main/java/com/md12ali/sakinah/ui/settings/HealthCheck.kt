package com.md12ali.sakinah.ui.settings

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.md12ali.sakinah.alarm.AdhanLog
import com.md12ali.sakinah.alarm.AdhanScheduler
import com.md12ali.sakinah.audio.AdhanAudio
import com.md12ali.sakinah.audio.AdhanPlaybackService
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.ui.components.IconBadge
import com.md12ali.sakinah.ui.components.SoftCard
import kotlinx.coroutines.delay

private data class Check(val ok: Boolean, val title: String, val detail: String, val fixLabel: String? = null, val fix: (() -> Unit)? = null)

/**
 * Checks everything that can stop the Adhan on a real phone and offers a one-tap fix for each.
 * Re-checks whenever you come back to the app (e.g. after changing a system setting).
 */
@Composable
fun AdhanHealthCheck(onDownloadAdhan: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }   // refresh after downloads / tests
    var showLog by remember { mutableStateOf(false) }

    val checks = remember(tick) { runChecks(context, onDownloadAdhan) }
    val allOk = checks.all { it.ok }
    val cs = MaterialTheme.colorScheme

    SoftCard(selected = !allOk) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            IconBadge(Icons.Outlined.HealthAndSafety, if (allOk) cs.primary else cs.error,
                if (allOk) cs.primaryContainer else cs.errorContainer, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Adhan health check", style = MaterialTheme.typography.titleMedium)
                Text(if (allOk) "Everything is ready – the Adhan will play on time." else "${checks.count { !it.ok }} thing(s) can stop the Adhan – tap Fix",
                    style = MaterialTheme.typography.bodySmall, color = if (allOk) cs.primary else cs.error)
            }
        }
        checks.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = cs.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (c.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null,
                    tint = if (c.ok) cs.primary else cs.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = MaterialTheme.typography.titleSmall)
                    Text(c.detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                if (!c.ok && c.fix != null) FilledTonalButton(onClick = c.fix, shape = RoundedCornerShape(50)) { Text(c.fixLabel ?: "Fix") }
            }
        }
        Spacer(Modifier.padding(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { AdhanPlaybackService.preview(context, Prayer.DHUHR, "") }, shape = RoundedCornerShape(50)) { Text("Play now") }
            OutlinedButton(onClick = { AdhanScheduler.scheduleTest(context); tick++ }, shape = RoundedCornerShape(50)) { Text("Test in 1 min") }
            TextButton(onClick = { AdhanPlaybackService.stop(context) }) { Text("Stop") }
        }
        Text("“Test in 1 min” is a real alarm: lock the phone and wait – the Adhan screen and sound should appear.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Hide what happened" else "Show what happened (log)") }
        if (showLog) {
            val lines = remember(tick) { AdhanLog.lines(context) }
            Text(if (lines.isEmpty()) "Nothing yet – the log fills when an Adhan alarm fires." else lines.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@SuppressLint("BatteryLife")
private fun runChecks(context: Context, onDownloadAdhan: () -> Unit): List<Check> {
    val list = mutableListOf<Check>()
    val pkg = context.packageName
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    // 1. Next Adhan scheduled
    val next = AdhanLog.next(context)
    list += Check(next != null, "Next Adhan scheduled", next ?: "Open the Prayer tab once so the alarms are set",
        null, null)

    // 2. Exact alarms
    val exact = AdhanScheduler.canScheduleExact(context)
    list += Check(exact, "Exact alarms allowed", if (exact) "Adhan fires at the exact minute" else "Android may delay the Adhan",
        "Allow") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")))
    }

    // 3. Notifications (needed for the Adhan screen + Stop button)
    val notif = NotificationManagerCompat.from(context).areNotificationsEnabled()
    list += Check(notif, "Notifications on", if (notif) "Adhan screen and Stop button can show" else "Turn on so the Adhan can show and play",
        "Turn on") {
        open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
    }

    // 4. Full-screen Adhan over lock screen (Android 14+ can switch this off)
    if (Build.VERSION.SDK_INT >= 34) {
        val fsi = context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        list += Check(fsi, "Adhan screen on lock screen", if (fsi) "Shows like an alarm clock" else "Allow full-screen alerts", "Allow") {
            open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")))
        }
    }

    // 5. Battery – the #1 reason Adhan apps go silent on Samsung / Xiaomi / Oppo / Huawei
    val pm = context.getSystemService(PowerManager::class.java)
    val unrestricted = pm.isIgnoringBatteryOptimizations(pkg)
    list += Check(unrestricted, "Battery: unrestricted",
        if (unrestricted) "The phone won't put Azan to sleep" else "Your phone may block the Adhan to save battery" + makerTip(),
        "Allow") {
        open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")))
    }

    // 6. Alarm volume
    val am = context.getSystemService(AudioManager::class.java)
    val vol = am.getStreamVolume(AudioManager.STREAM_ALARM)
    val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    list += Check(vol * 100 / max.coerceAtLeast(1) >= 30, "Alarm volume", "Currently ${vol * 100 / max.coerceAtLeast(1)}% – the Adhan uses alarm volume",
        "Raise") {
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.75).toInt(), AudioManager.FLAG_SHOW_UI) }
    }

    // 7. Real Adhan recording available
    val have = AdhanAudio.downloaded(context).size
    list += Check(have > 0, "Real Adhan recording", if (have > 0) "$have recordings saved on the phone (work offline)" else "Not downloaded yet – needs internet once",
        "Download") { onDownloadAdhan() }

    return list
}

private fun makerTip(): String = when (Build.MANUFACTURER.lowercase()) {
    "samsung" -> ". Samsung: also Settings → Battery → Background usage limits → remove Azan from 'Sleeping apps'."
    "xiaomi", "redmi", "poco" -> ". Xiaomi: also turn on Autostart for Azan (Security app → Permissions → Autostart)."
    "oppo", "realme", "oneplus" -> ". Also allow 'Auto launch' / 'Allow background activity' for Azan."
    "huawei", "honor" -> ". Huawei: App launch → Azan → Manage manually → turn all on."
    "vivo" -> ". Vivo: allow 'Background power consumption' and Autostart for Azan."
    else -> ""
}
