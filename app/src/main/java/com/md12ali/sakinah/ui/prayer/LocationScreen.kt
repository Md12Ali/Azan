package com.md12ali.sakinah.ui.prayer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.md12ali.sakinah.ui.components.IconBadge
import com.md12ali.sakinah.ui.components.SectionHeader
import com.md12ali.sakinah.ui.components.SoftCard

/**
 * Auto (GPS) follows the nearest UK city's central mosque; or pick a mosque / a city.
 * The 🔗 button attaches any Masjidbox / Mawaqit / website / CSV timetable link to a mosque.
 */
@Composable
fun LocationScreen(vm: PrayerViewModel, onDone: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var linkFor by remember { mutableStateOf<MosqueProfile?>(null) }
    var query by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) { vm.useAutoGps(); onDone() } else vm.onLocationPermissionResult(false)
    }
    val q = query.trim().lowercase()
    val mosques = Places.mosques.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.city.lowercase().contains(q) }
    val cities = Places.ukCities.filter { q.isEmpty() || it.name.lowercase().contains(q) }
    val cs = MaterialTheme.colorScheme

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Mosque & location", style = MaterialTheme.typography.headlineMedium)
            Text("Prayer times follow your city's central mosque", style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant)
            Spacer(Modifier.padding(6.dp))
            val auto = s.settings.mode == LocationMode.AUTO_GPS
            SoftCard(selected = auto, onClick = {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.MyLocation, cs.onPrimary, cs.primary, 46.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Automatic (GPS)", style = MaterialTheme.typography.titleMedium)
                        Text("Uses the central mosque of whichever UK city you're in – switches by itself when you travel.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    if (auto) Icon(Icons.Outlined.CheckCircle, null, tint = cs.primary)
                }
            }
            Spacer(Modifier.padding(4.dp))
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("Search mosque or city") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true, shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = cs.surface, focusedContainerColor = cs.surface)
            )
            SectionHeader("Central mosques")
        }
        items(mosques, key = { it.id }) { m ->
            val selected = s.settings.mode == LocationMode.MOSQUE && s.settings.mosqueId == m.id
            val linked = s.settings.mosqueSources[m.id]
            SoftCard(selected = selected, onClick = { vm.chooseMosque(m); onDone() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Mosque, cs.primary, cs.primaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Text(m.city + " · " + (linked?.let { "times from ${TimetableFetcher.describe(it)}" } ?: "auto timetable"),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    IconButton(onClick = { linkFor = m }) { Icon(Icons.Outlined.Link, "Attach timetable link", tint = cs.onSurfaceVariant) }
                    if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = cs.primary)
                }
            }
        }
        item { SectionHeader("City only (calculated times)") }
        items(cities, key = { "city_" + it.name }) { city ->
            val selected = s.settings.mode == LocationMode.CITY_CALC && s.settings.cityName == city.name
            SoftCard(selected = selected, onClick = { vm.chooseCity(city); onDone() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.LocationCity, cs.tertiary, cs.surfaceVariant, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(city.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = cs.primary)
                }
            }
        }
    }

    linkFor?.let { m ->
        var text by remember(m.id) { mutableStateOf(s.settings.mosqueSources[m.id].orEmpty()) }
        AlertDialog(
            onDismissRequest = { linkFor = null },
            title = { Text(m.name) },
            text = {
                Column {
                    Text("Paste the mosque's timetable page – Masjidbox, Mawaqit, its website, or a CSV link.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                        placeholder = { Text("https://…") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                }
            },
            confirmButton = { TextButton(onClick = { vm.attachTimetableLink(m, text); linkFor = null }) { Text("Save & download") } },
            dismissButton = { TextButton(onClick = { linkFor = null }) { Text("Cancel") } }
        )
    }
}
