package com.azeroth.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Paleta
//
// Noche amaderada: casi negro con un punto cálido, cuatro escalones de
// elevación y un único acento morado (el color de Midnight). El dorado se
// reserva para lo que de verdad es especial —recompensas, épico— y el rojo
// solo para errores. Un acento que se usa para todo deja de ser un acento.
// ---------------------------------------------------------------------------
internal val Ink = Color(0xFF0D0A07)        // fondo
internal val Surface1 = Color(0xFF15110C)   // panel
internal val Surface2 = Color(0xFF1D1811)   // panel elevado
internal val Surface3 = Color(0xFF272016)   // panel sobre panel
internal val Hairline = Color(0xFF2E2619)   // separadores: se intuyen, no se ven

internal val Arcane = Color(0xFFA98BFF)     // acento principal (Midnight)
internal val ArcaneDim = Color(0xFF6B54B8)
internal val ArcaneWash = Color(0xFF241C3D)  // fondo teñido del acento
internal val Gold = Color(0xFFE0B457)        // recompensas y logros
internal val GoldWash = Color(0xFF322512)

internal val TextHigh = Color(0xFFF2EADD)    // títulos y cifras
internal val TextMid = Color(0xFFB7A991)     // cuerpo secundario
internal val TextLow = Color(0xFF7C7161)     // etiquetas y metadatos

internal val Positive = Color(0xFF5FCB7C)
internal val Warning = Color(0xFFE2A33C)
internal val Danger = Color(0xFFE5645B)

private val Scheme = darkColorScheme(
    primary = Arcane,
    onPrimary = Color(0xFF150E29),
    primaryContainer = ArcaneWash,
    onPrimaryContainer = Color(0xFFDCCFFF),
    inversePrimary = ArcaneDim,
    secondary = Gold,
    onSecondary = Color(0xFF1F1500),
    secondaryContainer = GoldWash,
    onSecondaryContainer = Color(0xFFF7DFA8),
    tertiary = Color(0xFF8FD3E8),
    background = Ink,
    onBackground = TextHigh,
    surface = Surface1,
    onSurface = TextHigh,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface2,
    surfaceContainerHigh = Surface3,
    surfaceContainerHighest = Surface3,
    outline = Hairline,
    outlineVariant = Color(0xFF241E15),
    scrim = Color(0xCC000000),
    error = Danger,
    onError = Color(0xFF2A0906),
    errorContainer = Color(0xFF3E1512),
    onErrorContainer = Color(0xFFF8C4C0),
)

// ---------------------------------------------------------------------------
// Tipografía
//
// Una sola familia, jerarquía por peso y tamaño en vez de por color de fondo.
// Los títulos van apretados (letterSpacing negativo) porque a tamaño grande el
// tracking por defecto de Material se ve suelto; las etiquetas van abiertas y
// en versalita para que se lean como etiquetas y no compitan con el contenido.
// ---------------------------------------------------------------------------
private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    height: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
) = TextStyle(
    fontSize = size.sp,
    lineHeight = height.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = lineHeightStyle,
)

internal val AppTypography = Typography(
    displayLarge = style(44, 48, FontWeight.Bold, -1.0),
    displayMedium = style(36, 40, FontWeight.Bold, -0.8),
    displaySmall = style(30, 36, FontWeight.Bold, -0.6),
    headlineLarge = style(26, 32, FontWeight.SemiBold, -0.4),
    headlineMedium = style(22, 28, FontWeight.SemiBold, -0.3),
    headlineSmall = style(19, 25, FontWeight.SemiBold, -0.2),
    titleLarge = style(17, 23, FontWeight.SemiBold, -0.1),
    titleMedium = style(15, 21, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.Medium),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 21, FontWeight.Normal, 0.1),
    bodySmall = style(13, 19, FontWeight.Normal, 0.1),
    labelLarge = style(14, 18, FontWeight.SemiBold, 0.2),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.6),
    labelSmall = style(11, 14, FontWeight.Medium, 0.8),
)

@Composable
fun AzerothTheme(
    // Siempre oscuro: la app tiene identidad propia y no sigue el claro/oscuro
    // del sistema.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography,
        content = content,
    )
}
