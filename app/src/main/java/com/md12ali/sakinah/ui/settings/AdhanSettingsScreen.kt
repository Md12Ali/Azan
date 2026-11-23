package com.md12ali.sakinah.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.md12ali.sakinah.audio.AdhanPlaybackService
import com.md12ali.sakinah.core.prayer.CalculationMethod
import com.md12ali.sakinah.core.prayer.Madhab
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.data.settings.AlertMode
import com.md12ali.sakinah.ui.prayer.PrayerViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdhanSettingsScreen(vm: PrayerViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sounds = remember { AdhanPlaybackService.availableSounds(context) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Adhan & alerts", style = MaterialTheme.typography.headlineMedium)

        Section("Alert for each prayer") {
            Prayer.adhanPrayers.forEach { p ->
                Text(p.english, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlertMode.entries.forEach { mode ->
                        FilterChip(selected = s.settings.alerts[p] == mode, onClick = { vm.setAlert(p, mode) }, label = { Text(mode.label) })
                    }
                }
            }
        }

        Section("Adhan sound") {
            if (sounds.isEmpty()) {
                Text("No Adhan files bundled yet – the phone's alarm tone will be used. Add MP3s to app/src/main/assets/adhan/ (see README).",
                    style = MaterialTheme.typography.bodySmall)
            }
            SoundPicker("Fajr (calm/soft)", s.settings.fajrSound, sounds, onPick = vm::setFajrSound) {
                AdhanPlaybackService.preview(context, Prayer.FAJR, it)
            }
            SoundPicker("Dhuhr, Asr, Maghrib, Isha", s.settings.mainSound, sounds, onPick = vm::setMainSound) {
                AdhanPlaybackService.preview(context, Prayer.DHUHR, it)
            }
            OutlinedButton(onClick = { AdhanPlaybackService.stop(context) }) {
                Icon(Icons.Outlined.Stop, null); Text(" Stop preview")
            }
        }

        Section("Behaviour") {
            ToggleRow("Use alarm volume", "Adhan follows alarm volume, so it still plays when media is muted.",
                s.settings.useAlarmVolume, vm::setUseAlarmVolume)
            ToggleRow("Adhan at jamaat time", "When the mosque publishes jamaat times, alert at jamaat instead of the start time.",
                s.settings.alertAtJamaat, vm::setAlertAtJamaat)
        }

        Section("Calculation (used when no mosque timetable is available)") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CalculationMethod.entries.forEach { m ->
                    FilterChip(selected = s.settings.method == m, onClick = { vm.setMethod(m) }, label = { Text(m.label) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Madhab.entries.forEach { m ->
                    FilterChip(selected = s.settings.madhab == m, onClick = { vm.setMadhab(m) }, label = { Text(m.label) })
                }
            }
        }

        Section("Reliability") {
            Text("Some phones (Samsung, Xiaomi, Huawei, OnePlus) stop background alarms to save battery. " +
                "Set Sakinah to 'Unrestricted' battery use so the Adhan always plays.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }) { Text("Battery settings") }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:${context.packageName}")))
                }) { Text("Alarms & reminders permission") }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundPicker(label: String, current: String, options: List<String>, onPick: (String) -> Unit, onPreview: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(current.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onPreview(current) }) { Icon(Icons.Outlined.PlayArrow, "Preview") }
    }
    if (options.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == current, onClick = { onPick(o) }, label = { Text(o.substringAfterLast('/')) })
        }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
