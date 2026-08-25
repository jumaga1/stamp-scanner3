package com.filatelia.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StampPrimary = Color(0xFF2E5266)
private val StampSecondary = Color(0xFF6E8898)
private val StampAccent = Color(0xFFC9A227)
private val StampBackground = Color(0xFFF5F2EA)

private val LightColors = lightColorScheme(
    primary = StampPrimary,
    secondary = StampSecondary,
    tertiary = StampAccent,
    background = StampBackground
)

private val DarkColors = darkColorScheme(
    primary = StampSecondary,
    secondary = StampPrimary,
    tertiary = StampAccent
)

@Composable
fun StampScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
