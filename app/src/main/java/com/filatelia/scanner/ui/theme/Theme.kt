package com.filatelia.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NavyPrimary = Color(0xFF1B3B6F)
val NavySecondary = Color(0xFF28536B)
val GoldTertiary = Color(0xFFC29B38)
val WarmBackground = Color(0xFFF9F9FB)
val SurfaceCard = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF1A1C1E)

val LightColors = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = NavySecondary,
    onSecondary = Color.White,
    tertiary = GoldTertiary,
    onTertiary = Color.White,
    background = WarmBackground,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF003062),
    secondary = Color(0xFFB5CCE9),
    tertiary = Color(0xFFE5C158),
    background = Color(0xFF121316),
    surface = Color(0xFF1E1F24)
)

@Composable
fun StampScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
