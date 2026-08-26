package com.filatelia.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta Plateada / Titanio Metálico
val SilverDarkNavy = Color(0xFF1E2836)
val SilverMetallic = Color(0xFFC0C7CE)
val SilverLight = Color(0xFFECEFF2)
val SilverBackground = Color(0xFFE2E6EA)
val SilverSurface = Color(0xFFF5F7FA)
val SilverCardBorder = Color(0xFFB8C2CC)
val SilverAccentTeal = Color(0xFF2B4C6F)
val ValuationGold = Color(0xFFC59B27)
val ValuationGoldContainer = Color(0xFFFFF9E6)

val LightColors = lightColorScheme(
    primary = SilverDarkNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE2E8),
    onPrimaryContainer = SilverDarkNavy,
    secondary = SilverAccentTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0DCE8),
    onSecondaryContainer = Color(0xFF112233),
    tertiary = ValuationGold,
    onTertiary = Color(0xFF3B2E00),
    tertiaryContainer = ValuationGoldContainer,
    onTertiaryContainer = Color(0xFF4D3800),
    background = SilverBackground,
    surface = SilverSurface,
    surfaceVariant = Color(0xFFD8DEE4),
    onSurfaceVariant = Color(0xFF454F5B),
    outline = SilverCardBorder
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFB0BAC5),
    onPrimary = Color(0xFF111822),
    secondary = Color(0xFF8FA9C4),
    tertiary = ValuationGold,
    background = Color(0xFF181E26),
    surface = Color(0xFF222B36),
    surfaceVariant = Color(0xFF2E3845),
    outline = Color(0xFF4A5568)
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
