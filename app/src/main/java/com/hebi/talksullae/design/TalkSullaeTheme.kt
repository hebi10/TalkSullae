package com.hebi.talksullae.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkPalette = darkColorScheme(
    primary = Color(0xFFF4F4F5),
    onPrimary = Color(0xFF151619),
    background = Color(0xFF101114),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF1D1F23),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF272A30),
    onSurfaceVariant = Color(0xFFADB0B7),
    outline = Color(0xFF393C43),
    error = Color(0xFFE09393),
    onError = Color(0xFF101114)
)

private val LightPalette = lightColorScheme(
    primary = Color(0xFF18191C),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF18191C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18191C),
    surfaceVariant = Color(0xFFF0F1F3),
    onSurfaceVariant = Color(0xFF62656B),
    outline = Color(0xFFDADDE1),
    error = Color(0xFFB84343),
    onError = Color(0xFFFFFFFF)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun TalkSullaeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkPalette else LightPalette,
        typography = AppTypography,
        content = content
    )
}
