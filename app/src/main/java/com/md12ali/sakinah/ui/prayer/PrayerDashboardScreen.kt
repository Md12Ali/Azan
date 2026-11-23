package com.md12ali.sakinah.ui.prayer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.core.prayer.TimeSource
import com.md12ali.sakinah.data.settings.AlertMode
import com.md12ali.sakinah.ui.components.IconBadge
import com.md12ali.sakinah.ui.components.MosqueSilhouette
import com.md12ali.sakinah.ui.components.Pill
import com.md12ali.sakinah.ui.components.StarField
import com.md12ali.sakinah.ui.components.SunArc
import com.md12ali.sakinah.ui.components.prayerIcon
import com.md12ali.sakinah.ui.theme.Gold
import com.md12ali.sakinah.ui.theme.GoldSoft
import com.md12ali.sakinah.ui.theme.arabicStyle
import com.md12ali.sakinah.ui.theme.isNight
import com.md12ali.sakinah.ui.theme.skyFor
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

@Composable
fun PrayerDashboardScreen(vm: PrayerViewModel, onOpenLocation: () -> Unit, onOpenAdhanSettings: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        vm.onLocationPermissionResult(res.values.any { it })
    }
    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION); add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Hero(s, onOpenLocation, onRefresh = { vm.refresh(fetchGps = true, forceDownload = true) })

        // Content sheet overlapping the sky
        Column(
            Modifier.fillMaxWidth().offset(y = (-28).dp)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(s.busyMessage != null) {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
                    Text(s.busyMessage.orEmpty(), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }

            if (!s.exactAlarmAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                NoticeCard("Allow exact Adhan timing", "So the Adhan plays exactly on time, allow 'Alarms & reminders'.", "Allow") {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:${context.packageName}")))
                }
            }
            s.message?.let { NoticeCard("Note", it, "OK") { vm.consumeMessage() } }

            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Today's prayers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(s.now.format(dayFmt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val current = s.today?.currentAt(s.now)
            Prayer.entries.forEach { p ->
                val begin = s.today?.begins?.get(p)
                PrayerCard(
                    prayer = p,
                    begin = begin?.format(hm) ?: "--:--",
                    jamaat = s.today?.jamaat?.get(p)?.format(hm),
                    isNext = p == s.next?.first && s.next?.second?.toLocalDate() == s.now.toLocalDate(),
                    isCurrent = p == current,
                    isPast = begin != null && begin.isBefore(s.now) && p != current,
                    alert = s.settings.alerts[p],
                    onAlertClick = { vm.cycleAlert(p) }
                )
            }

            s.today?.let { day ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Verified, null, Modifier.size(16.dp),
                        tint = if (day.source == TimeSource.MOSQUE_TIMETABLE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(6.dp))
                    Text(day.sourceDetail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onOpenAdhanSettings, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Tap a bell to change its alert · Adhan settings")
            }
        }
    }
}

@Composable
private fun Hero(s: PrayerUiState, onOpenLocation: () -> Unit, onRefresh: () -> Unit) {
    val period = s.today?.currentAt(s.now) ?: Prayer.ISHA
    val next = s.next
    val remaining = next?.let { Duration.between(s.now, it.second) } ?: Duration.ZERO
    val sunrise = s.today?.begins?.get(Prayer.SUNRISE)
    val sunset = s.today?.begins?.get(Prayer.MAGHRIB)
    val sunProgress = if (sunrise != null && sunset != null && s.now.isAfter(sunrise) && s.now.isBefore(sunset))
        Duration.between(sunrise, s.now).seconds.toFloat() / Duration.between(sunrise, sunset).seconds else null

    Crossfade(period, label = "sky") { p ->
        Box(Modifier.fillMaxWidth().background(skyFor(p))) {
            if (isNight(p)) StarField(Modifier.matchParentSize())
            SunArc(sunProgress, Modifier.fillMaxWidth().height(150.dp).align(Alignment.BottomCenter).padding(bottom = 40.dp))
            MosqueSilhouette(Modifier.fillMaxWidth().height(130.dp).align(Alignment.BottomCenter).padding(bottom = 24.dp))

            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 64.dp)) {
                // Place row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpenLocation)
                            .background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.LocationOn, null, Modifier.size(18.dp), tint = GoldSoft)
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.place?.title ?: "Finding location…", color = Color.White,
                                style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(s.place?.subtitle.orEmpty(), color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        Icon(Icons.Outlined.ExpandMore, null, tint = Color.White.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onRefresh, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f))) {
                        Icon(Icons.Outlined.Refresh, "Refresh", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text(if (s.hijri.isNotBlank()) s.hijri else " ", color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
                Text("Next · ${next?.first?.english ?: ""}", color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(next?.first?.arabic.orEmpty(), style = arabicStyle(30), color = Gold,
                    modifier = Modifier.align(Alignment.CenterHorizontally))

                val total = remaining.seconds.coerceAtLeast(0)
                Text(
                    "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60),
                    color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Pill(
                    "Adhan at ${next?.second?.format(hm) ?: "--:--"}",
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
                    container = Color.White.copy(alpha = 0.18f), content = Color.White
                )
                Spacer(Modifier.height(70.dp))
            }
        }
    }
}

@Composable
private fun PrayerCard(
    prayer: Prayer, begin: String, jamaat: String?, isNext: Boolean, isCurrent: Boolean, isPast: Boolean,
    alert: AlertMode?, onAlertClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val container = when {
        isNext -> cs.primary
        isCurrent -> cs.primaryContainer
        else -> cs.surface
    }
    val onContainer = when {
        isNext -> cs.onPrimary
        isCurrent -> cs.onPrimaryContainer
        else -> cs.onSurface
    }
    val alpha = if (isPast && !isNext) 0.55f else 1f
    Surface(
        shape = RoundedCornerShape(22.dp), color = container,
        border = if (isNext || isCurrent) null else androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                prayerIcon(prayer),
                tint = if (isNext) Gold else cs.primary.copy(alpha = alpha),
                background = if (isNext) Color.White.copy(alpha = 0.15f) else cs.primaryContainer.copy(alpha = 0.6f * alpha + 0.2f),
                size = 42.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(prayer.english, style = MaterialTheme.typography.titleMedium, color = onContainer.copy(alpha = alpha),
                        fontWeight = if (isNext || isCurrent) FontWeight.SemiBold else FontWeight.Medium)
                    if (isCurrent) Pill("Now", Modifier.padding(start = 8.dp))
                    if (isNext) Pill("Next", Modifier.padding(start = 8.dp), container = Gold, content = Color(0xFF2A2210))
                }
                Text(prayer.arabic, style = arabicStyle(15), color = (if (isNext) GoldSoft else cs.onSurfaceVariant).copy(alpha = alpha))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(begin, style = MaterialTheme.typography.titleLarge, color = onContainer.copy(alpha = alpha), fontWeight = FontWeight.Medium)
                if (jamaat != null) Text("Jamaat $jamaat", style = MaterialTheme.typography.labelMedium,
                    color = (if (isNext) GoldSoft else cs.primary).copy(alpha = alpha))
            }
            if (prayer.hasAdhan) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onAlertClick) {
                    Icon(
                        when (alert) {
                            AlertMode.ADHAN, AlertMode.SOFT_ADHAN -> Icons.Outlined.NotificationsActive
                            AlertMode.GENTLE -> Icons.Outlined.Notifications
                            else -> Icons.Outlined.NotificationsOff
                        },
                        contentDescription = "Alert: ${alert?.label}",
                        tint = when {
                            alert == AlertMode.SILENT -> onContainer.copy(alpha = 0.35f)
                            isNext -> Gold
                            else -> cs.primary
                        }
                    )
                }
            } else Spacer(Modifier.width(52.dp))
        }
    }
}

@Composable
fun NoticeCard(title: String, body: String, action: String, onAction: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
