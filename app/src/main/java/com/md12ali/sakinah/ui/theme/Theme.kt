package com.md12ali.sakinah.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp

// Calm palette: deep teal night, warm sand, muted gold, sage.
val Teal900 = Color(0xFF0E3B3A)
val Teal700 = Color(0xFF175E5B)
val Teal500 = Color(0xFF2E8C86)
val Sand50 = Color(0xFFF7F2E8)
val Sand100 = Color(0xFFEFE6D4)
val Gold = Color(0xFFC9A44C)
val Sage = Color(0xFF9DB8A8)
val Ink = Color(0xFF1F2A2A)

private val Light = lightColorScheme(
    primary = Teal700, onPrimary = Sand50,
    primaryContainer = Color(0xFFD6E9E4), onPrimaryContainer = Teal900,
    secondary = Gold, onSecondary = Ink,
    secondaryContainer = Color(0xFFF3E6C4), onSecondaryContainer = Ink,
    tertiary = Sage,
    background = Sand50, onBackground = Ink,
    surface = Color(0xFFFFFCF6), onSurface = Ink,
    surfaceVariant = Sand100, onSurfaceVariant = Color(0xFF4F5B58),
    outline = Color(0xFFCFC4AE)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7CC7BE), onPrimary = Teal900,
    primaryContainer = Teal700, onPrimaryContainer = Sand50,
    secondary = Gold, onSecondary = Ink,
    secondaryContainer = Color(0xFF4A3F22), onSecondaryContainer = Sand50,
    tertiary = Sage,
    background = Color(0xFF0A2423), onBackground = Sand50,
    surface = Color(0xFF0F2F2E), onSurface = Sand50,
    surfaceVariant = Color(0xFF173E3C), onSurfaceVariant = Color(0xFFC4D3CE),
    outline = Color(0xFF2F5653)
)

/**
 * Arabic text style. For the best Quran/Qaida rendering drop a free OFL font such as
 * "Amiri Quran" or "Scheherazade New" into res/font/ and replace FontFamily.Serif with
 * FontFamily(Font(R.font.amiri_quran)).
 */
val ArabicFont: FontFamily = FontFamily.Serif

fun arabicStyle(size: Int) = TextStyle(
    fontFamily = ArabicFont, fontSize = size.sp, lineHeight = (size * 1.9).sp,
    textDirection = TextDirection.Rtl, fontWeight = FontWeight.Normal
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Medium),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun SakinahTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
