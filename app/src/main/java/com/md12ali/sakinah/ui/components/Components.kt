package com.md12ali.sakinah.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Very faint 8-point star (Rub el Hizb) lattice – adds an Islamic feel without visual noise. */
@Composable
fun PatternBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val lineColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.035f)
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 72.dp.toPx()
            val r = step * 0.32f
            var y = 0f
            while (y < size.height + step) {
                var x = 0f
                while (x < size.width + step) {
                    drawPath(eightPointStar(Offset(x, y), r), lineColor, style = Stroke(1.dp.toPx()))
                    x += step
                }
                y += step
            }
        }
        content()
    }
}

private fun eightPointStar(c: Offset, r: Float): Path = Path().apply {
    // Two overlapping squares rotated 45° = Rub el Hizb.
    for (rot in listOf(0.0, Math.PI / 4)) {
        for (i in 0..4) {
            val a = rot + i * Math.PI / 2 + Math.PI / 4
            val p = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
