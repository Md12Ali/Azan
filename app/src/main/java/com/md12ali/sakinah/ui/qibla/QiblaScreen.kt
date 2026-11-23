package com.md12ali.sakinah.ui.qibla

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.md12ali.sakinah.SakinahApp
import com.md12ali.sakinah.core.qibla.Qibla
import com.md12ali.sakinah.ui.components.IconBadge
import com.md12ali.sakinah.ui.components.SoftCard
import com.md12ali.sakinah.ui.prayer.PrayerViewModel
import com.md12ali.sakinah.ui.theme.Gold
import com.md12ali.sakinah.ui.theme.Navy900
import com.md12ali.sakinah.ui.theme.Navy950
import com.md12ali.sakinah.ui.theme.Teal500
import com.md12ali.sakinah.ui.theme.arabicStyle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Qibla compass – works anywhere in the world, offline.
 * Heading comes from the rotation-vector sensor (falls back to accelerometer + magnetometer),
 * corrected from magnetic to TRUE north with Android's built-in World Magnetic Model (GeomagneticField).
 */
@Composable
fun QiblaScreen(vm: PrayerViewModel) {
    val context = LocalContext.current
    val s by vm.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    // Location: fresh GPS if possible, otherwise the place the prayer times use.
    var lat by remember { mutableStateOf<Double?>(null) }
    var lon by remember { mutableStateOf<Double?>(null) }
    var fromGps by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val loc = (context.applicationContext as SakinahApp).container.location.currentLocation()
        if (loc != null) { lat = loc.latitude; lon = loc.longitude; fromGps = true }
    }
    val useLat = lat ?: s.place?.latitude
    val useLon = lon ?: s.place?.longitude

    // Compass heading (degrees from true north)
    var heading by remember { mutableFloatStateOf(0f) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var hasSensor by remember { mutableStateOf(true) }
    val declination = remember(useLat, useLon) {
        if (useLat == null || useLon == null) 0f
        else GeomagneticField(useLat.toFloat(), useLon.toFloat(), 0f, System.currentTimeMillis()).declination
    }
    DisposableEffect(declination) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        hasSensor = rot != null || (acc != null && mag != null)
        val r = FloatArray(9); val o = FloatArray(3)
        var grav: FloatArray? = null; var geo: FloatArray? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> SensorManager.getRotationMatrixFromVector(r, e.values)
                    Sensor.TYPE_ACCELEROMETER -> { grav = e.values.clone(); if (rot != null) return }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        geo = e.values.clone()
                        if (rot != null) { accuracy = e.accuracy; return }
                    }
                }
                if (e.sensor.type != Sensor.TYPE_ROTATION_VECTOR) {
                    val g = grav ?: return; val m = geo ?: return
                    if (!SensorManager.getRotationMatrix(r, null, g, m)) return
                }
                SensorManager.getOrientation(r, o)
                val raw = ((Math.toDegrees(o[0].toDouble()).toFloat() + declination) + 360f) % 360f
                // Low-pass filter on the circle (handles 359°→0° wrap) for a calm, steady needle.
                val d = Qibla.delta(heading.toDouble(), raw.toDouble()).toFloat()
                heading = (heading + d * 0.15f + 360f) % 360f
            }
            override fun onAccuracyChanged(sensor: Sensor, a: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) accuracy = a
            }
        }
        rot?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        mag?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }   // for accuracy reports / fallback
        if (rot == null) acc?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(listener) }
    }

    val qibla = if (useLat != null && useLon != null) Qibla.bearing(useLat, useLon) else null
    val dist = if (useLat != null && useLon != null) Qibla.distanceKm(useLat, useLon) else null
    val turn = qibla?.let { Qibla.delta(heading.toDouble(), it) }
    val aligned = turn != null && abs(turn) <= 4.0 && hasSensor

    // One gentle vibration each time you come into line with the Qibla.
    var wasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(aligned) {
        if (aligned && !wasAligned) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        wasAligned = aligned
    }

    val ringColor by animateColorAsState(if (aligned) Gold else Color.White.copy(alpha = 0.25f), label = "ring")
    // heading is already smoothed on the circle, so rotate directly (no wrap-around spin at 0°/360°).
    val dialRotation = -heading

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF010F0B), Navy950, Color(0xFF07402F))))
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 110.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Qibla", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text("القبلة", style = arabicStyle(22), color = Gold)
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                qibla == null -> "Finding your location…"
                !hasSensor -> "This phone has no compass – use the bearing below"
                aligned -> "You are facing the Qibla"
                turn!! > 0 -> "Turn right %.0f°".format(turn)
                else -> "Turn left %.0f°".format(-turn!!)
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (aligned) Gold else Color.White.copy(alpha = 0.85f),
            fontWeight = if (aligned) FontWeight.SemiBold else FontWeight.Normal
        )
        Spacer(Modifier.height(20.dp))

        // ---------------- Compass dial ----------------
        Box(Modifier.fillMaxWidth(0.88f).aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val radius = size.minDimension / 2
                // glow when aligned
                if (aligned) drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = 0.35f), Color.Transparent), c, radius), radius)
                drawCircle(Color.White.copy(alpha = 0.05f), radius * 0.92f, c)
                drawCircle(ringColor, radius * 0.92f, c, style = Stroke(2.dp.toPx()))

                rotate(dialRotation, c) {
                    // ticks
                    for (deg in 0 until 360 step 5) {
                        val major = deg % 30 == 0
                        val a = Math.toRadians(deg.toDouble() - 90)
                        val r1 = radius * (if (major) 0.80f else 0.85f)
                        val r2 = radius * 0.89f
                        drawLine(Color.White.copy(alpha = if (major) 0.7f else 0.25f),
                            Offset(c.x + r1 * cos(a).toFloat(), c.y + r1 * sin(a).toFloat()),
                            Offset(c.x + r2 * cos(a).toFloat(), c.y + r2 * sin(a).toFloat()),
                            if (major) 2.dp.toPx() else 1.dp.toPx())
                    }
                    // cardinal letters (kept upright relative to the dial)
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 16.dp.toPx(); typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (label, deg) ->
                        val a = Math.toRadians(deg.toDouble() - 90)
                        val rr = radius * 0.70f
                        paint.color = (if (label == "N") Color(0xFFFF7A6B) else Color.White).toArgb()
                        drawContext.canvas.nativeCanvas.drawText(label,
                            c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat() + paint.textSize / 3, paint)
                    }
                    // Qibla marker + line
                    qibla?.let { q ->
                        val a = Math.toRadians(q - 90)
                        val tip = Offset(c.x + radius * 0.80f * cos(a).toFloat(), c.y + radius * 0.80f * sin(a).toFloat())
                        drawLine(Gold.copy(alpha = 0.55f), c, tip, 3.dp.toPx())
                        // Kaaba: a small cube with a gold band
                        val k = 16.dp.toPx()
                        val kc = Offset(c.x + radius * 0.80f * cos(a).toFloat(), c.y + radius * 0.80f * sin(a).toFloat())
                        rotate(q.toFloat(), kc) {
                            drawRect(Color(0xFF111111), Offset(kc.x - k / 2, kc.y - k / 2), androidx.compose.ui.geometry.Size(k, k))
                            drawRect(Gold, Offset(kc.x - k / 2, kc.y - k / 2 + k * 0.22f), androidx.compose.ui.geometry.Size(k, k * 0.14f))
                            drawRect(Gold, Offset(kc.x - k / 2, kc.y - k / 2), androidx.compose.ui.geometry.Size(k, k), style = Stroke(1.dp.toPx()))
                        }
                    }
                }

                // Fixed phone-direction needle (always points up)
                val needle = Path().apply {
                    moveTo(c.x, c.y - radius * 0.62f)
                    lineTo(c.x - 10.dp.toPx(), c.y)
                    lineTo(c.x, c.y - 8.dp.toPx())
                    lineTo(c.x + 10.dp.toPx(), c.y)
                    close()
                }
                drawPath(needle, if (aligned) Gold else Teal500)
                drawCircle(Color.White, 6.dp.toPx(), c)
                drawCircle(Navy900, 3.dp.toPx(), c)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---------------- Info cards ----------------
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoTile(Modifier.weight(1f), "Qibla direction",
                qibla?.let { "%.1f°".format(it) } ?: "—", qibla?.let { "${Qibla.compassPoint(it)} from true north" } ?: "")
            InfoTile(Modifier.weight(1f), "Distance to Makkah",
                dist?.let { "%,.0f km".format(it) } ?: "—", dist?.let { "%,.0f miles".format(it * 0.621371) } ?: "")
        }
        Spacer(Modifier.height(12.dp))
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.MyLocation, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                Spacer(Modifier.padding(6.dp))
                Column {
                    Text(if (fromGps) "Using your GPS location" else "Using ${s.place?.title ?: "your saved place"}",
                        style = MaterialTheme.typography.titleSmall)
                    if (useLat != null && useLon != null)
                        Text("%.4f, %.4f".format(useLat, useLon), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW && hasSensor) {
            Spacer(Modifier.height(12.dp))
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.ScreenRotation, Gold, MaterialTheme.colorScheme.secondaryContainer)
                    Spacer(Modifier.padding(6.dp))
                    Text("Compass needs calibrating: move your phone in a figure-of-8 a few times, away from metal and magnets.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.Explore, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                Spacer(Modifier.padding(6.dp))
                Text("Hold your phone flat. Turn until the needle points at the Kaaba – it turns gold and vibrates when you face the Qibla.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InfoTile(modifier: Modifier, label: String, value: String, sub: String) {
    Column(
        modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp)).padding(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Start)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Gold)
    }
}
