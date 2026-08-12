package com.azeroth.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Dirección
//
// Dos intentos anteriores trataron de que la app PAREZCA World of Warcraft
// imitando su interfaz: primero con morado y degradados, luego con oro,
// pergamino y bordes metálicos. Los dos fallaron por lo mismo — la interfaz del
// juego está pensada para un monitor de 27 pulgadas y un ratón; a 390px de
// ancho, el filigrana dorado no se lee como épico, se lee como recargado y
// viejo. Las propias apps móviles de Blizzard no hacen eso.
//
// La dirección ahora es la contraria: **el chrome se calla y el arte del juego
// pone la identidad.** La app está llena de World of Warcraft —el render de tu
// personaje, los mapas reales de zona, los iconos de objeto, los colores de
// clase y de calidad— así que la interfaz que los envuelve no necesita gritar.
// Fondo neutro, tipografía clara, y el color de TU clase como único acento.
// ---------------------------------------------------------------------------

// Grises fríos y neutros: dejan que el arte del juego, que es cálido y
// saturado, sea lo único con color en la pantalla.
internal val Base = Color(0xFF0E0F13)        // fondo
internal val Surface = Color(0xFF171920)     // panel
internal val SurfaceHigh = Color(0xFF20232C) // panel elevado, fila resaltada
internal val Line = Color(0xFF2A2E38)        // separadores

internal val TextHigh = Color(0xFFECEDF0)
internal val TextMid = Color(0xFFA3A8B4)
internal val TextLow = Color(0xFF6C7280)

internal val Positive = Color(0xFF4ADE80)
internal val Warning = Color(0xFFFBBF24)
internal val Danger = Color(0xFFF87171)

// El dorado se conserva SOLO para recompensas y dinero, que es donde el juego
// lo usa. Ya no es color de marca.
internal val Gold = Color(0xFFE3B341)

// Nombres que el resto del código todavía usa.
internal val Arcane = ClassColors.Unknown

/**
 * Color de la clase del personaje activo. Es el acento de toda la interfaz:
 * botones, enlaces, barras de progreso y el borde del retrato.
 */
val LocalAccent = staticCompositionLocalOf { ClassColors.Unknown }

private fun scheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF0B0D11),
    primaryContainer = accent.copy(alpha = 0.16f),
    onPrimaryContainer = accent,
    inversePrimary = accent.copy(alpha = 0.6f),
    secondary = Gold,
    onSecondary = Color(0xFF1B1405),
    secondaryContainer = Color(0xFF2A2110),
    onSecondaryContainer = Gold,
    tertiary = accent,
    background = Base,
    onBackground = TextHigh,
    surface = Surface,
    onSurface = TextHigh,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextMid,
    surfaceContainerLowest = Base,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = Color(0xFF272B36),
    outline = Line,
    outlineVariant = Color(0xFF1F222A),
    scrim = Color(0xCC000000),
    error = Danger,
    onError = Color(0xFF2A0906),
    errorContainer = Color(0xFF3B1513),
    onErrorContainer = Color(0xFFFECACA),
)

// ---------------------------------------------------------------------------
// Tipografía
//
// Una familia, jerarquía por tamaño y peso. Las cifras van tabulares para que
// una columna de ilvl, de oro o de nivel de llave no baile al cambiar un dígito.
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
    tabular: Boolean = false,
) = TextStyle(
    fontSize = size.sp,
    lineHeight = height.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = lineHeightStyle,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

internal val AppTypography = Typography(
    displayLarge = style(40, 44, FontWeight.Bold, -1.0, tabular = true),
    displayMedium = style(32, 36, FontWeight.Bold, -0.8, tabular = true),
    displaySmall = style(27, 32, FontWeight.Bold, -0.6, tabular = true),
    headlineLarge = style(24, 30, FontWeight.Bold, -0.5),
    headlineMedium = style(21, 27, FontWeight.SemiBold, -0.3, tabular = true),
    headlineSmall = style(18, 24, FontWeight.SemiBold, -0.2, tabular = true),
    titleLarge = style(17, 23, FontWeight.SemiBold, -0.1),
    titleMedium = style(15, 21, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.Medium),
    bodyLarge = style(16, 25, FontWeight.Normal),
    bodyMedium = style(14, 21, FontWeight.Normal, 0.1),
    bodySmall = style(13, 19, FontWeight.Normal, 0.1),
    labelLarge = style(14, 18, FontWeight.SemiBold, 0.2),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.5, tabular = true),
    labelSmall = style(11, 15, FontWeight.Medium, 0.6, tabular = true),
)

@Composable
fun AzerothTheme(
    /** Clase del personaje activo; de ella sale el acento de toda la app. */
    accent: Color = ClassColors.Unknown,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = scheme(accent),
            typography = AppTypography,
            content = content,
        )
    }
}
