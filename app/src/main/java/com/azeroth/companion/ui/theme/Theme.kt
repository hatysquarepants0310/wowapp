package com.azeroth.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta profesional: maderas oscuras (espresso/nogal) con acentos morados
// del color de la expansión Midnight, y un dorado tenue como acento cálido.
private val Espresso = Color(0xFF15100B)   // fondo casi negro amaderado
private val Walnut = Color(0xFF1E1811)     // superficie
private val WalnutHi = Color(0xFF2A2118)   // superficie elevada
private val WalnutBorder = Color(0xFF3A2E22)
private val Midnight = Color(0xFF9B7BFF)    // morado principal (Midnight)
private val MidnightDeep = Color(0xFF3B2A66)
private val MidnightSoft = Color(0xFFCEBEFF)
private val Gold = Color(0xFFD9A441)        // acento cálido (madera/oro)
private val Parchment = Color(0xFFEDE3D4)   // texto principal
private val ParchmentDim = Color(0xFFA8987F) // texto secundario
private val Danger = Color(0xFFE5534B)

private val WoodDarkScheme = darkColorScheme(
    primary = Midnight,
    onPrimary = Color(0xFF12091F),
    primaryContainer = MidnightDeep,
    onPrimaryContainer = MidnightSoft,
    secondary = Gold,
    onSecondary = Color(0xFF241800),
    secondaryContainer = Color(0xFF4A380F),
    onSecondaryContainer = Color(0xFFF3D9A0),
    tertiary = MidnightSoft,
    background = Espresso,
    onBackground = Parchment,
    surface = Walnut,
    onSurface = Parchment,
    surfaceVariant = WalnutHi,
    onSurfaceVariant = ParchmentDim,
    surfaceContainer = WalnutHi,
    surfaceContainerHigh = Color(0xFF322619),
    outline = WalnutBorder,
    outlineVariant = Color(0xFF2A2118),
    error = Danger,
    onError = Color(0xFF2A0906),
    errorContainer = Color(0xFF4A1512),
    onErrorContainer = Color(0xFFF7C0BC),
)

@Composable
fun AzerothTheme(
    // Tema oscuro amaderado siempre: la app tiene una identidad visual propia,
    // no sigue el claro/oscuro del sistema.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = WoodDarkScheme,
        typography = Typography(),
        content = content,
    )
}
