package com.md12ali.sakinah.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.md12ali.sakinah.core.prayer.Prayer

// ---- Palette: night navy, deep teal, warm sand, soft gold -------------------------------
val Navy950 = Color(0xFF070F1F)
val Navy900 = Color(0xFF0B1730)
val Navy800 = Color(0xFF14254A)
val Teal900 = Color(0xFF0E3B3A)
val Teal700 = Color(0xFF17635E)
val Teal500 = Color(0xFF2F9C93)
val Sand50 = Color(0xFFFAF6EE)
val Sand100 = Color(0xFFF1E9DA)
val Gold = Color(0xFFD9B26A)
val GoldSoft = Color(0xFFF2DFB3)
val Ink = Color(0xFF16202B)

private val Light = lightColorScheme(
    primary = Teal700, onPrimary = Color.White,
    primaryContainer = Color(0xFFD8EEEA), onPrimaryContainer = Teal900,
    secondary = Gold, onSecondary = Ink,
    secondaryContainer = Color(0xFFF7EBCF), onSecondaryContainer = Color(0xFF4A3A12),
    tertiary = Navy800,
    background = Sand50, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Sand100, onSurfaceVariant = Color(0xFF5E6873),
    outline = Color(0xFFD9CFBC), outlineVariant = Color(0xFFEDE5D6)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD3C8), onPrimary = Teal900,
    primaryContainer = Color(0xFF15433F), onPrimaryContainer = Color(0xFFD8EEEA),
    secondary = Gold, onSecondary = Ink,
    secondaryContainer = Color(0xFF3B3020), onSecondaryContainer = GoldSoft,
    tertiary = Color(0xFF9DB4E8),
    background = Navy950, onBackground = Sand50,
    surface = Navy900, onSurface = Sand50,
    surfaceVariant = Navy800, onSurfaceVariant = Color(0xFFB7C2D6),
    outline = Color(0xFF2A3A60), outlineVariant = Color(0xFF1C2B4D)
)

/** Sky gradients for each part of the day – the hero card follows the current prayer period. */
fun skyFor(period: Prayer?): Brush = when (period) {
    Prayer.FAJR -> Brush.verticalGradient(listOf(Color(0xFF1B1F4B), Color(0xFF51407A), Color(0xFFC98A9B)))
    Prayer.SUNRISE -> Brush.verticalGradient(listOf(Color(0xFF3A6EA5), Color(0xFF8DB7D8), Color(0xFFF4CFA4)))
    Prayer.DHUHR -> Brush.verticalGradient(listOf(Color(0xFF1F6F8B), Color(0xFF52A7B5), Color(0xFFB9DFD8)))
    Prayer.ASR -> Brush.verticalGradient(listOf(Color(0xFF2B5E78), Color(0xFFC0906A), Color(0xFFF1C98E)))
    Prayer.MAGHRIB -> Brush.verticalGradient(listOf(Color(0xFF2A1E4F), Color(0xFF8E3F62), Color(0xFFF08A5D)))
    Prayer.ISHA, null -> Brush.verticalGradient(listOf(Navy950, Navy900, Color(0xFF1D3263)))
}

/** Whether to draw stars in the sky. */
fun isNight(period: Prayer?) = period == Prayer.ISHA || period == null || period == Prayer.FAJR

/**
 * Arabic text style. For the best rendering drop a free OFL font such as "Amiri" or
 * "Scheherazade New" into res/font/ and replace FontFamily.Serif with FontFamily(Font(R.font.amiri)).
 */
val ArabicFont: FontFamily = FontFamily.Serif

fun arabicStyle(size: Int) = TextStyle(
    fontFamily = ArabicFont, fontSize = size.sp, lineHeight = (size * 1.6).sp,
    textDirection = TextDirection.Rtl, fontWeight = FontWeight.Normal
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.ExtraLight, letterSpacing = (-1).sp),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Light),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = labelLarge.copy(letterSpacing = 0.4.sp)
    )
}

@Composable
fun SakinahTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
