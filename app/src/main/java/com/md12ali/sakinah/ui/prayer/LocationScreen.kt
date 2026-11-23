package com.md12ali.sakinah.ui.prayer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.md12ali.sakinah.core.location.MosqueProfile
import com.md12ali.sakinah.core.location.Places
import com.md12ali.sakinah.data.remote.TimetableFetcher
import com.md12ali.sakinah.data.settings.LocationMode

/**
 * Three ways to choose times:
 *  • Auto (GPS) – follows the nearest city's central mosque timetable anywhere in the UK
 *  • A specific mosque – e.g. keep your home mosque's times while travelling
 *  • A city – plain calculation
 * Each mosque can be given a Masjidbox / Mawaqit / CSV / website link (link icon).
 */
@Composable
fun LocationScreen(vm: PrayerViewModel, onDone: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var linkFor by remember { mutableStateOf<MosqueProfile?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) { vm.useAutoGps(); onDone() } else vm.onLocationPermissionResult(false)
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Location & mosque", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp))
            val auto = s.settings.mode == LocationMode.AUTO_GPS
            Card(
                Modifier.fillMaxWidth().clickable {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (auto) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MyLocation, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Automatic (GPS) – recommended", style = MaterialTheme.typography.titleMedium)
                        Text("Uses the central mosque timetable of whichever UK city you're in. Changes automatically when you travel.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (auto) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text("Central mosques", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        }
        items(Places.mosques, key = { it.id }) { m ->
            val selected = s.settings.mode == LocationMode.MOSQUE && s.settings.mosqueId == m.id
            val linked = s.settings.mosqueSources[m.id]
            Card(
                Modifier.fillMaxWidth().clickable { vm.chooseMosque(m); onDone() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            m.city + " · " + if (linked != null) "timetable from ${TimetableFetcher.describe(linked)}" else "auto-detect timetable",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { linkFor = m }) { Icon(Icons.Outlined.Link, "Attach timetable link") }
                    if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Text("City (calculated times, no mosque)", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        }
        items(Places.ukCities, key = { "city_" + it.name }) { city ->
            val selected = s.settings.mode == LocationMode.CITY_CALC && s.settings.cityName == city.name
            Text(
                city.name + if (selected) "  ✓" else "",
                Modifier.fillMaxWidth().clickable { vm.chooseCity(city); onDone() }.padding(vertical = 12.dp, horizontal = 8.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        item { Spacer(Modifier.padding(24.dp)) }
    }

    linkFor?.let { m ->
        var text by remember(m.id) { mutableStateOf(s.settings.mosqueSources[m.id].orEmpty()) }
        AlertDialog(
            onDismissRequest = { linkFor = null },
            title = { Text("Timetable for ${m.name}") },
            text = {
                Column {
                    Text("Paste the mosque's Masjidbox or Mawaqit page, its website, or a CSV link (e.g. a Google Sheet published as CSV).",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                        placeholder = { Text("https://masjidbox.com/prayer-times/…") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.attachTimetableLink(m, text); linkFor = null }) { Text("Save & download") } },
            dismissButton = { TextButton(onClick = { linkFor = null }) { Text("Cancel") } }
        )
    }
}
