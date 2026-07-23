package com.azeroth.companion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Violet = Color(0xFF7C5CFF)
private val VioletSoft = Color(0xFFC9B8FF)
private val Ink = Color(0xFF0D1117)
private val Surface = Color(0xFF161B22)
private val Amber = Color(0xFFF0B429)

private val DarkScheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A1F5E),
    onPrimaryContainer = VioletSoft,
    secondary = Amber,
    background = Ink,
    surface = Surface,
    surfaceVariant = Color(0xFF21262E),
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFF9BA4B0),
    error = Color(0xFFF85149),
)

private val LightScheme = lightColorScheme(primary = Violet, secondary = Amber)

@Composable
fun AzerothTheme(
    // Tema oscuro por defecto (§1); el claro solo si el sistema lo fuerza.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme || isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
