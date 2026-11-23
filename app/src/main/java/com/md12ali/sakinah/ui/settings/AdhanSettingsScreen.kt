package com.md12ali.sakinah.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.md12ali.sakinah.alarm.AdhanScheduler
import com.md12ali.sakinah.audio.AdhanPlaybackService
import com.md12ali.sakinah.core.prayer.CalculationMethod
import com.md12ali.sakinah.core.prayer.Madhab
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.data.settings.AlertMode
import com.md12ali.sakinah.ui.components.IconBadge
import com.md12ali.sakinah.ui.components.SoftCard
import com.md12ali.sakinah.ui.components.prayerIcon
import com.md12ali.sakinah.ui.prayer.PrayerViewModel
import com.md12ali.sakinah.ui.theme.arabicStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdhanSettingsScreen(vm: PrayerViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sounds by remember { mutableStateOf(AdhanPlaybackService.availableSounds(context)) }
    var pickFor by remember { mutableStateOf<String?>(null) }   // "fajr" | "main"
    // Refresh the sound list after background downloads finish.
    androidx.compose.runtime.LaunchedEffect(s.busyMessage, s.message) { sounds = AdhanPlaybackService.availableSounds(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            AdhanPlaybackService.importSound(context, uri)?.let { path ->
                if (pickFor == "fajr") vm.setFajrSound(path) else vm.setMainSound(path)
                sounds = AdhanPlaybackService.availableSounds(context)
            }
        }
    }
    val cs = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Adhan", style = MaterialTheme.typography.headlineMedium)
        Text("Choose how each prayer is announced", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)

        AdhanHealthCheck(onDownloadAdhan = { vm.downloadAdhan(); sounds = AdhanPlaybackService.availableSounds(context) })

        SoftCard {
            CardTitle(Icons.Outlined.Alarm, "Alert for each prayer")
            Prayer.adhanPrayers.forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = cs.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(prayerIcon(p), null, tint = cs.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(p.english, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(p.arabic, style = arabicStyle(15), color = cs.onSurfaceVariant)
                }
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlertMode.entries.forEach { mode ->
                        FilterChip(
                            selected = s.settings.alerts[p] == mode, onClick = { vm.setAlert(p, mode) },
                            label = { Text(mode.label) }, shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = cs.primary, selectedLabelColor = cs.onPrimary)
                        )
                    }
                }
            }
        }

        SoftCard {
            CardTitle(Icons.Outlined.MusicNote, "Adhan sound")
            Text("Real Adhan recordings download automatically (once) and then work offline. You can also use your own MP3.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            SoundPicker("Fajr (calm)", s.settings.fajrSound, sounds, vm::setFajrSound) { AdhanPlaybackService.preview(context, Prayer.FAJR, it) }
            FilledTonalButton(onClick = { pickFor = "fajr"; picker.launch(arrayOf("audio/*")) }, shape = RoundedCornerShape(50)) {
                Icon(Icons.Outlined.LibraryMusic, null); Spacer(Modifier.width(6.dp)); Text("Choose Fajr Adhan from phone")
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = cs.outlineVariant)
            SoundPicker("Dhuhr · Asr · Maghrib · Isha", s.settings.mainSound, sounds, vm::setMainSound) { AdhanPlaybackService.preview(context, Prayer.DHUHR, it) }
            FilledTonalButton(onClick = { pickFor = "main"; picker.launch(arrayOf("audio/*")) }, shape = RoundedCornerShape(50)) {
                Icon(Icons.Outlined.LibraryMusic, null); Spacer(Modifier.width(6.dp)); Text("Choose Adhan from phone")
            }
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(onClick = { AdhanPlaybackService.stop(context) }, shape = RoundedCornerShape(50)) {
                Icon(Icons.Outlined.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stop preview")
            }
        }

        SoftCard {
            CardTitle(Icons.Outlined.Tune, "Behaviour")
            ToggleRow("Use alarm volume", "Plays even when media volume is muted.", s.settings.useAlarmVolume, vm::setUseAlarmVolume)
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = cs.outlineVariant)
            ToggleRow("Adhan at jamaat time", "Alert at the mosque's jamaat time instead of the start time.", s.settings.alertAtJamaat, vm::setAlertAtJamaat)
        }

        SoftCard {
            CardTitle(Icons.Outlined.Calculate, "Asr & calculation")
            Text("Asr school (also picks Mithl 1/2 from mosque tables)", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = s.settings.autoMadhab, onClick = { vm.setMadhab(null) },
                    label = { Text("Automatic (${s.settings.effectiveMadhab.label.substringBefore(" (")})") }, shape = RoundedCornerShape(50))
                Madhab.entries.forEach { m ->
                    FilterChip(selected = !s.settings.autoMadhab && s.settings.madhab == m, onClick = { vm.setMadhab(m) }, label = { Text(m.label) }, shape = RoundedCornerShape(50))
                }
            }
            Spacer(Modifier.padding(4.dp))
            Text("Calculation method (when no mosque timetable is used)", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = s.settings.autoMethod, onClick = { vm.setMethod(null) },
                    label = { Text("Automatic by country (${s.settings.effectiveMethod.label})") }, shape = RoundedCornerShape(50))
                CalculationMethod.entries.forEach { m ->
                    FilterChip(selected = !s.settings.autoMethod && s.settings.method == m, onClick = { vm.setMethod(m) }, label = { Text(m.label) }, shape = RoundedCornerShape(50))
                }
            }
        }

        SoftCard {
            CardTitle(Icons.Outlined.MusicNote, "About the Adhan recordings")
            com.md12ali.sakinah.audio.AdhanAudio.catalog.forEach { r ->
                Text("${r.title} – ${r.credit} (${r.license})", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            Text("Recordings from Wikimedia Commons, used under their free licences.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }

        SoftCard {
            CardTitle(Icons.Outlined.BatteryChargingFull, "Make sure it always plays")
            Text("Some phones (Samsung, Xiaomi, OnePlus) block background alarms. Set Azan's battery use to 'Unrestricted'.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.padding(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                    shape = RoundedCornerShape(50)) { Text("Battery") }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    FilledTonalButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            android.net.Uri.parse("package:${context.packageName}")))
                    }, shape = RoundedCornerShape(50)) { Text("Exact alarms") }
                }
            }
        }
    }
}

@Composable
private fun CardTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, 34.dp)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundPicker(label: String, current: String, options: List<String>, onPick: (String) -> Unit, onPreview: (String) -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(AdhanPlaybackService.label(context, current), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onPreview(current) }) { Icon(Icons.Outlined.PlayArrow, "Preview", tint = MaterialTheme.colorScheme.primary) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = current.isBlank(), onClick = { onPick("") }, label = { Text("Automatic") }, shape = RoundedCornerShape(50))
        options.forEach { o ->
            FilterChip(selected = o == current, onClick = { onPick(o) }, label = { Text(AdhanPlaybackService.label(context, o)) }, shape = RoundedCornerShape(50))
        }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
