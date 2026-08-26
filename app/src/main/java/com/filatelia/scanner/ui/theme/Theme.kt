package com.filatelia.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandNavy = Color(0xFF0F1E36)
val BrandGold = Color(0xFFD4AF37)
val BrandGoldLight = Color(0xFFFFF8E7)
val AccentTeal = Color(0xFF1B6B93)
val CardBackground = Color(0xFFFFFFFF)
val PageBackground = Color(0xFFF4F6F9)

val LightColors = lightColorScheme(
    primary = BrandNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2EAF4),
    onPrimaryContainer = BrandNavy,
    secondary = AccentTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E9F7),
    onSecondaryContainer = Color(0xFF003554),
    tertiary = BrandGold,
    onTertiary = Color(0xFF3E2D00),
    tertiaryContainer = BrandGoldLight,
    onTertiaryContainer = Color(0xFF4C3600),
    background = PageBackground,
    surface = CardBackground,
    surfaceVariant = Color(0xFFEAEEF3),
    onSurfaceVariant = Color(0xFF454B54)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF90B5E8),
    onPrimary = Color(0xFF0F1E36),
    secondary = Color(0xFF67B5E8),
    tertiary = BrandGold,
    background = Color(0xFF10141D),
    surface = Color(0xFF171D28),
    surfaceVariant = Color(0xFF222B3A)
)

@Composable
fun StampScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
