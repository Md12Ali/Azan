package com.md12ali.sakinah.ui.prayer

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.md12ali.sakinah.core.location.NearbyMosque
import com.md12ali.sakinah.data.settings.LocationMode
import com.md12ali.sakinah.ui.components.IconBadge
import com.md12ali.sakinah.ui.components.Pill
import com.md12ali.sakinah.ui.components.SoftCard
import com.md12ali.sakinah.ui.theme.Gold

/**
 * Mosques near you – anywhere in the world.
 * Each card shows distance, whether the mosque publishes its own timetable, today's times (if published),
 * "Follow" (use its times for the Azan) and "Directions".
 */
@Composable
fun MosquesScreen(vm: PrayerViewModel) {
    val ui by vm.nearby.collectAsStateWithLifecycle()
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.findNearby(force = true) }

    LaunchedEffect(Unit) { if (!ui.searched) vm.findNearby() }

    val followingId = when (s.settings.mode) {
        LocationMode.MOSQUE -> s.settings.mosqueId?.let { if (it.startsWith("osm_") || it.startsWith("mawaqit_")) it else "central_$it" }
        LocationMode.AUTO_GPS -> s.place?.mosque?.id?.let { if (it.startsWith("osm_") || it.startsWith("mawaqit_")) it else "central_$it" }
        else -> null
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mosques near you", style = MaterialTheme.typography.headlineMedium)
                    Text("Anywhere in the world · nearest first", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                IconButton(onClick = {
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }) { Icon(Icons.Outlined.Refresh, "Search again", tint = cs.primary) }
            }
            Spacer(Modifier.height(10.dp))
            SoftCard(selected = s.settings.mode == LocationMode.AUTO_GPS && s.settings.followNearestMosque) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.MyLocation, cs.onPrimary, cs.primary, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Follow the nearest mosque automatically", style = MaterialTheme.typography.titleSmall)
                        Text("Wherever you travel, the Azan follows the timetable of the closest mosque that publishes one. Otherwise times are calculated for your exact location.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(
                        checked = s.settings.mode == LocationMode.AUTO_GPS && s.settings.followNearestMosque,
                        onCheckedChange = { on -> if (on) { vm.useAutoGps(); vm.setFollowNearest(true) } else vm.setFollowNearest(false) }
                    )
                }
            }
        }

        when {
            ui.loading && ui.mosques.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Finding mosques around you…", color = cs.onSurfaceVariant)
                }
            }
            ui.error != null && ui.mosques.isEmpty() -> item {
                SoftCard {
                    Text(ui.error.orEmpty(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
                        shape = RoundedCornerShape(50)) { Text("Try again") }
                }
            }
        }

        items(ui.mosques, key = { it.id }) { m ->
            MosqueCard(
                m = m,
                following = m.id == followingId,
                onFollow = { vm.followMosque(m) },
                onDirections = {
                    val uri = Uri.parse("geo:0,0?q=${m.latitude},${m.longitude}(${Uri.encode(m.name)})")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }
            )
        }

        if (ui.mosques.isNotEmpty()) item {
            Text("Mosque data © OpenStreetMap contributors (ODbL) and Mawaqit. Timetables are the mosques' own.",
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MosqueCard(m: NearbyMosque, following: Boolean, onFollow: () -> Unit, onDirections: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    SoftCard(selected = following) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(Icons.Outlined.Mosque, if (m.mawaqitSlug != null || m.source == "uk-central") Gold else cs.primary,
                cs.primaryContainer, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(distanceLabel(m.distanceKm), m.address).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        m.mawaqitSlug != null || m.source == "uk-central" ->
                            Pill("Own timetable", container = cs.primary, content = cs.onPrimary)
                        m.website != null -> Pill("Website – checking timetable")
                        else -> Pill("Calculated times", container = cs.surfaceVariant, content = cs.onSurfaceVariant)
                    }
                    if (following) Pill("Following", container = Gold, content = Color(0xFF2A2210))
                }
            }
            if (following) Icon(Icons.Outlined.CheckCircle, null, tint = cs.primary)
        }
        if (m.todayTimes.size >= 5) {
            Spacer(Modifier.height(10.dp))
            val names = if (m.todayTimes.size == 6) listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
                        else listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
            Row(Modifier.fillMaxWidth().background(cs.surfaceVariant, RoundedCornerShape(16.dp)).padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                names.zip(m.todayTimes).filter { it.first != "Sunrise" }.forEach { (n, t) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(n, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        Text(t, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!following) Button(onClick = onFollow, shape = RoundedCornerShape(50)) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Use for Azan")
            }
            OutlinedButton(onClick = onDirections, shape = RoundedCornerShape(50)) {
                Icon(Icons.Outlined.Directions, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Directions")
            }
        }
    }
}

private fun distanceLabel(km: Double): String = when {
    km < 1 -> "${(km * 1000).toInt()} m"
    km < 10 -> "%.1f km".format(km)
    else -> "%.0f km".format(km)
} + " (%.1f mi)".format(km * 0.621371)
