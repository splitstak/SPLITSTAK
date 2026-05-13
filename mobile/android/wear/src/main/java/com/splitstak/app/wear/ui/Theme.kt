package com.splitstak.app.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography

/** SPLITSTAK palette mirrored from CLAUDE.md. */
object SplitstakColors {
    val Bg = Color(0xFF0A0A0A)
    val Surface = Color(0xFF161616)
    val Surface2 = Color(0xFF1F1F1F)
    val Border = Color(0xFF2A2A2A)
    val Text = Color(0xFFF5F5F5)
    val TextDim = Color(0xFF888888)
    val TextFaint = Color(0xFF555555)
    val Accent = Color(0xFFFF5722)
    val AccentDim = Color(0xFF4A1A08)
}

private val splitstakColors = Colors(
    primary = SplitstakColors.Accent,
    primaryVariant = SplitstakColors.AccentDim,
    secondary = SplitstakColors.Surface2,
    secondaryVariant = SplitstakColors.Surface,
    background = SplitstakColors.Bg,
    surface = SplitstakColors.Surface,
    error = Color(0xFFFF4444),
    onPrimary = SplitstakColors.Bg,
    onSecondary = SplitstakColors.Text,
    onBackground = SplitstakColors.Text,
    onSurface = SplitstakColors.Text,
    onSurfaceVariant = SplitstakColors.TextDim,
    onError = SplitstakColors.Bg
)

/**
 * Typography biases toward condensed display + monospace numerics so the
 * watch matches the SPLITSTAK look. Wear OS doesn't auto-load the Bebas Neue
 * + JetBrains Mono webfonts the PWA uses, so we approximate with SansSerif
 * (bold) for display and Monospace for numerics.
 */
private val splitstakTypography = Typography(
    display1 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 0.04.em),
    display2 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.04.em),
    display3 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.04.em),
    title1 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.04.em),
    title2 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.04.em),
    title3 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.06.em),
    body1 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    body2 = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    button = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.1.em),
    caption1 = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 0.1.em),
    caption2 = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 0.1.em),
    caption3 = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, letterSpacing = 0.12.em)
)

@Composable
fun SplitstakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = splitstakColors,
        typography = splitstakTypography,
        content = content
    )
}
