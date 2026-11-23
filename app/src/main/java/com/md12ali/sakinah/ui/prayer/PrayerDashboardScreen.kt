package com.md12ali.sakinah.ui.prayer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.core.prayer.TimeSource
import com.md12ali.sakinah.data.settings.AlertMode
import com.md12ali.sakinah.ui.components.Chip
import com.md12ali.sakinah.ui.theme.arabicStyle
import java.time.Duration
import java.time.format.DateTimeFormatter

private val hm = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun PrayerDashboardScreen(vm: PrayerViewModel, onOpenLocation: () -> Unit, onOpenAdhanSettings: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        vm.onLocationPermissionResult(res.values.any { it })
    }
    // Ask gently once on first open: location (for nearest mosque) + notifications (Android 13+).
    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION); add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header: place + date
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onOpenLocation)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(s.place?.title ?: "…", style = MaterialTheme.typography.titleLarge)
                }
                Text(s.place?.subtitle.orEmpty(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.refresh(fetchGps = true, forceDownload = true) }) { Icon(Icons.Outlined.Refresh, "Refresh") }
        }

        AnimatedVisibility(s.busyMessage != null) {
            Column { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(s.busyMessage.orEmpty(), style = MaterialTheme.typography.labelSmall) }
        }

        NextPrayerHero(s)

        if (!s.exactAlarmAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            NoticeCard(
                "Allow exact Adhan timing",
                "Android needs 'Alarms & reminders' access so the Adhan plays exactly on time.",
                "Allow"
            ) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:${context.packageName}")))
            }
        }

        s.message?.let { msg ->
            NoticeCard("Note", msg, "OK") { vm.consumeMessage() }
        }

        // Today's list
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Prayer", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Begins", Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.today?.jamaat?.isNotEmpty() == true)
                        Text("Jamaat", Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(40.dp))
                }
                val current = s.today?.currentAt(s.now)
                Prayer.entries.forEach { p ->
                    PrayerRow(
                        prayer = p,
                        begin = s.today?.begins?.get(p)?.format(hm) ?: "--:--",
                        jamaat = s.today?.jamaat?.get(p)?.format(hm),
                        showJamaatColumn = s.today?.jamaat?.isNotEmpty() == true,
                        highlighted = p == s.next?.first && s.next?.second?.toLocalDate() == s.now.toLocalDate(),
                        isCurrent = p == current,
                        alert = s.settings.alerts[p],
                        onAlertClick = { vm.cycleAlert(p) }
                    )
                }
            }
        }

        s.today?.let { day ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(if (day.source == TimeSource.MOSQUE_TIMETABLE) "Mosque timetable" else "Calculated")
                Text(day.sourceDetail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onOpenAdhanSettings) {
            Icon(Icons.Outlined.VolumeUp, null); Spacer(Modifier.width(8.dp)); Text("Adhan sounds & alerts")
        }
    }
}

@Composable
private fun NextPrayerHero(s: PrayerUiState) {
    val next = s.next
    val prev = s.previous
    val remaining = next?.let { Duration.between(s.now, it.second) } ?: Duration.ZERO
    val total = if (next != null && prev != null) Duration.between(prev, next.second).seconds.coerceAtLeast(1) else 1
    val progress = (1f - remaining.seconds.toFloat() / total).coerceIn(0f, 1f)
    val ringTrack = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
    val ringColor = MaterialTheme.colorScheme.secondary

    Box(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Next prayer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                Text(next?.first?.english ?: "—", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimary)
                Text(next?.first?.arabic.orEmpty(), style = arabicStyle(24), color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(8.dp))
                Text("at ${next?.second?.format(hm) ?: "--:--"}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                if (s.hijri.isNotBlank())
                    Text(s.hijri, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
            }
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(ringTrack, -90f, 360f, false, style = stroke)
                    drawArc(ringColor, -90f, 360f * progress, false, style = stroke)
                }
                // (toMinutesPart() is API 31+, so compute manually for minSdk 26)
                val totalSec = remaining.seconds.coerceAtLeast(0)
                val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val sec = totalSec % 60
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%d:%02d:%02d".format(h, m, sec), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                    Text("remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun PrayerRow(
    prayer: Prayer, begin: String, jamaat: String?, showJamaatColumn: Boolean,
    highlighted: Boolean, isCurrent: Boolean, alert: AlertMode?, onAlertClick: () -> Unit
) {
    val bg = when {
        highlighted -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).background(bg, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(prayer.english, style = MaterialTheme.typography.titleMedium,
                fontWeight = if (highlighted || isCurrent) FontWeight.SemiBold else FontWeight.Normal)
            Text(prayer.arabic, style = arabicStyle(14), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(begin, Modifier.width(64.dp), style = MaterialTheme.typography.titleMedium)
        if (showJamaatColumn) Text(jamaat ?: "", Modifier.width(64.dp), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)
        if (prayer.hasAdhan) {
            IconButton(onClick = onAlertClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    when (alert) {
                        AlertMode.ADHAN, AlertMode.SOFT_ADHAN -> Icons.Outlined.NotificationsActive
                        AlertMode.GENTLE -> Icons.Outlined.NotificationsNone
                        else -> Icons.Outlined.NotificationsOff
                    },
                    contentDescription = "Alert: ${alert?.label}",
                    tint = if (alert == AlertMode.SILENT) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                )
            }
        } else Spacer(Modifier.width(40.dp))
    }
}

@Composable
fun NoticeCard(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
