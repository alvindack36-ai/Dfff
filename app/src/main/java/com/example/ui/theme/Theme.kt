package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SimGateGreen,
    secondary = SimGateLightGreen,
    tertiary = StatusBlue,
    background = SlateBackground,
    surface = SlateSurface,
    surfaceVariant = SlateSurfaceVariant,
    onPrimary = TextPrimary,
    onSecondary = SlateBackground,
    onTertiary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    error = StatusRed
)

private val LightColorScheme = lightColorScheme(
    primary = SimGateGreen,
    secondary = SimGateDarkGreen,
    tertiary = StatusBlue,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onPrimary = TextPrimary,
    onSecondary = LightSurface,
    onTertiary = LightText,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = TextSecondary,
    outline = TextMuted,
    error = StatusRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark theme by default as we have a physical mock-up match
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
