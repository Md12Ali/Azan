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

// ---- Palette: Emerald & Gold ------------------------------------------------------------
val Emerald950 = Color(0xFF021C16)
val Emerald900 = Color(0xFF032E25)
val Emerald800 = Color(0xFF064E3B)
val Emerald700 = Color(0xFF065F46)
val Emerald600 = Color(0xFF047857)
val Emerald400 = Color(0xFF34D399)
val Gold = Color(0xFFD4AF37)
val GoldSoft = Color(0xFFF3DE9A)
val GoldDeep = Color(0xFFB08A1E)
val Cream = Color(0xFFFBF8F1)
val Cream100 = Color(0xFFF3EDDF)
val Ink = Color(0xFF0F1F1A)

// Old names kept so every screen picks up the new palette.
val Navy950 = Emerald950
val Navy900 = Emerald900
val Navy800 = Emerald800
val Teal900 = Emerald900
val Teal700 = Emerald700
val Teal500 = Emerald400
val Sand50 = Cream
val Sand100 = Cream100

private val Light = lightColorScheme(
    primary = Emerald700, onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F2E6), onPrimaryContainer = Emerald900,
    secondary = Gold, onSecondary = Ink,
    secondaryContainer = Color(0xFFF8ECC4), onSecondaryContainer = Color(0xFF4A3A0A),
    tertiary = GoldDeep,
    background = Cream, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Cream100, onSurfaceVariant = Color(0xFF55655F),
    outline = Color(0xFFD8CDB2), outlineVariant = Color(0xFFEDE5D2)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5FD8A8), onPrimary = Emerald950,
    primaryContainer = Emerald800, onPrimaryContainer = Color(0xFFD5F2E6),
    secondary = Gold, onSecondary = Ink,
    secondaryContainer = Color(0xFF3A3010), onSecondaryContainer = GoldSoft,
    tertiary = GoldSoft,
    background = Emerald950, onBackground = Cream,
    surface = Color(0xFF06291F), onSurface = Cream,
    surfaceVariant = Color(0xFF0B3A2D), onSurfaceVariant = Color(0xFFB5CCC2),
    outline = Color(0xFF1E5644), outlineVariant = Color(0xFF123F31)
)

/** Emerald skies tinted for each part of the day – the hero follows the current prayer period. */
fun skyFor(period: Prayer?): Brush = when (period) {
    Prayer.FAJR -> Brush.verticalGradient(listOf(Color(0xFF021C16), Color(0xFF0B4A47), Color(0xFF7A6A8E)))
    Prayer.SUNRISE -> Brush.verticalGradient(listOf(Color(0xFF064E3B), Color(0xFF0E7A62), Color(0xFFE2C27A)))
    Prayer.DHUHR -> Brush.verticalGradient(listOf(Color(0xFF04503D), Color(0xFF078A66), Color(0xFF3FBF94)))
    Prayer.ASR -> Brush.verticalGradient(listOf(Color(0xFF053F31), Color(0xFF0C6B52), Color(0xFFC9A440)))
    Prayer.MAGHRIB -> Brush.verticalGradient(listOf(Color(0xFF032E25), Color(0xFF5B3A2A), Color(0xFFE08A3C)))
    Prayer.ISHA, null -> Brush.verticalGradient(listOf(Color(0xFF010F0B), Emerald950, Color(0xFF07402F)))
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
