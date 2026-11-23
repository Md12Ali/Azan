package com.md12ali.sakinah.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness3
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.ui.theme.Gold
import com.md12ali.sakinah.ui.theme.GoldSoft
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun prayerIcon(p: Prayer): ImageVector = when (p) {
    Prayer.FAJR -> Icons.Outlined.Brightness3
    Prayer.SUNRISE -> Icons.Outlined.WbTwilight
    Prayer.DHUHR -> Icons.Outlined.LightMode
    Prayer.ASR -> Icons.Outlined.WbSunny
    Prayer.MAGHRIB -> Icons.Outlined.Brightness4
    Prayer.ISHA -> Icons.Outlined.NightsStay
}

/** Softly twinkling stars – drawn only for night skies. */
@Composable
fun StarField(modifier: Modifier = Modifier, count: Int = 46) {
    val stars = remember {
        val r = Random(7)
        List(count) { Triple(r.nextFloat(), r.nextFloat() * 0.75f, r.nextFloat()) }
    }
    val t by rememberInfiniteTransition(label = "stars").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse), label = "twinkle"
    )
    Canvas(modifier.fillMaxSize()) {
        stars.forEach { (x, y, phase) ->
            val a = 0.25f + 0.6f * ((sin((t + phase) * Math.PI * 2) + 1) / 2).toFloat()
            drawCircle(Color.White.copy(alpha = a), radius = (0.8f + phase * 1.4f).dp.toPx(), center = Offset(x * size.width, y * size.height))
        }
    }
}

/**
 * The sun's path between sunrise and sunset as a dashed arc, with the sun at [progress] (0..1).
 * Pass null to hide the sun (night).
 */
@Composable
fun SunArc(progress: Float?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val rect = Size(w * 0.9f, h * 2f)
        val topLeft = Offset(w * 0.05f, h * 0.1f)
        drawArc(
            Color.White.copy(alpha = 0.35f), 180f, 180f, false, topLeft, rect,
            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)))
        )
        drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, topLeft.y + rect.height / 2), Offset(w, topLeft.y + rect.height / 2), 1.dp.toPx())
        progress?.let { p ->
            val angle = Math.PI * (1 - p.coerceIn(0f, 1f))
            val cx = topLeft.x + rect.width / 2 + (rect.width / 2 * cos(angle)).toFloat()
            val cy = topLeft.y + rect.height / 2 - (rect.height / 2 * sin(angle)).toFloat()
            drawCircle(GoldSoft.copy(alpha = 0.35f), 14.dp.toPx(), Offset(cx, cy))
            drawCircle(Gold, 7.dp.toPx(), Offset(cx, cy))
        }
    }
}

/** An original, generic mosque silhouette (dome + two minarets) for the bottom of the hero. */
@Composable
fun MosqueSilhouette(modifier: Modifier = Modifier, color: Color = Color.White.copy(alpha = 0.12f)) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val base = h
        val cx = w / 2
        val path = Path().apply {
            // body
            moveTo(cx - w * 0.22f, base); lineTo(cx - w * 0.22f, base - h * 0.38f)
            lineTo(cx + w * 0.22f, base - h * 0.38f); lineTo(cx + w * 0.22f, base); close()
            // dome (onion-ish)
            moveTo(cx - w * 0.15f, base - h * 0.38f)
            cubicTo(cx - w * 0.17f, base - h * 0.62f, cx - w * 0.04f, base - h * 0.74f, cx, base - h * 0.86f)
            cubicTo(cx + w * 0.04f, base - h * 0.74f, cx + w * 0.17f, base - h * 0.62f, cx + w * 0.15f, base - h * 0.38f)
            close()
            // finial
            addOval(androidx.compose.ui.geometry.Rect(Offset(cx, base - h * 0.92f), h * 0.025f))
            // minarets
            for (side in listOf(-1f, 1f)) {
                val mx = cx + side * w * 0.33f
                moveTo(mx - w * 0.022f, base); lineTo(mx - w * 0.022f, base - h * 0.72f)
                lineTo(mx, base - h * 0.84f); lineTo(mx + w * 0.022f, base - h * 0.72f); lineTo(mx + w * 0.022f, base); close()
                addRect(androidx.compose.ui.geometry.Rect(mx - w * 0.032f, base - h * 0.58f, mx + w * 0.032f, base - h * 0.55f))
            }
        }
        drawPath(path, color)
    }
}

/** Fine gold Rub el Hizb (8-point star) lattice – an elegant Islamic pattern overlay. */
@Composable
fun GeometricPattern(modifier: Modifier = Modifier, color: Color = Gold.copy(alpha = 0.10f), cell: Dp = 56.dp) {
    Canvas(modifier) {
        val step = cell.toPx()
        val r = step * 0.30f
        var row = 0
        var y = 0f
        while (y < size.height + step) {
            var x = if (row % 2 == 0) 0f else step / 2
            while (x < size.width + step) {
                val path = Path()
                for (rot in listOf(0.0, Math.PI / 4)) {
                    for (i in 0..4) {
                        val a = rot + i * Math.PI / 2 + Math.PI / 4
                        val px = x + (r * cos(a)).toFloat(); val py = y + (r * sin(a)).toFloat()
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                }
                drawPath(path, color, style = Stroke(1.dp.toPx()))
                drawCircle(color, r * 0.28f, Offset(x, y), style = Stroke(1.dp.toPx()))
                x += step
            }
            y += step * 0.87f; row++
        }
    }
}

/** Round tinted icon badge. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, background: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.secondaryContainer,
         content: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = container) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = content)
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier) = Pill(text, modifier)

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

data class NavTab(val route: String, val label: String, val icon: ImageVector)

/** Floating pill navigation bar. */
@Composable
fun FloatingNavBar(tabs: List<NavTab>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.shadow(18.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black.copy(0.2f), spotColor = Color.Black.copy(0.25f))
        ) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tabs.forEach { t ->
                    val isSel = t.route == selected
                    Row(
                        Modifier.clip(RoundedCornerShape(26.dp))
                            .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onSelect(t.route) }
                            .padding(horizontal = if (isSel) 18.dp else 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(t.icon, t.label, Modifier.size(22.dp),
                            tint = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isSel) Text(t.label, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Rounded content card used across settings/location screens. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, selected: Boolean = false, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier.fillMaxWidth().clip(shape).let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant)
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
