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
// La referencia no es "fantasía": es la interfaz del propio juego, que es
// oscura, metálica y con trim dorado. Ver docs/direccion-de-arte.md.
//
// El diseño anterior era el default número uno de los que hay que evitar:
// negro cálido + acento morado + tarjetas redondeadas con sombra. Aquí el
// fondo es negro AZULADO (el del tooltip del juego), el acento es azul runa y
// la profundidad la da un borde, no una sombra. El morado se retira además
// porque competía con el #A335EE de calidad épica, que es intocable.
// ---------------------------------------------------------------------------
internal val Tinta = Color(0xFF0A0B0F)          // fondo
internal val Piedra = Color(0xFF14161D)         // panel
internal val PiedraAlta = Color(0xFF1D212B)     // panel sobre panel, filas alternas
internal val Bisel = Color(0xFF3A3428)          // borde metálico apagado
internal val OroTabardo = Color(0xFFC8A44D)     // trim vivo y títulos de sección
internal val Pergamino = Color(0xFFE8DCC0)      // texto principal, hueso cálido
internal val PergaminoMedio = Color(0xFF9A917E) // texto secundario
internal val PergaminoTenue = Color(0xFF6B6555) // etiquetas y metadatos
internal val Runa = Color(0xFF6E9FD4)           // único acento frío: lo interactivo

// Alias que el resto del código ya usa por nombre.
internal val Gold = OroTabardo
internal val Arcane = Runa

internal val Positive = Color(0xFF5FCB7C)
internal val Warning = Color(0xFFE2A33C)
internal val Danger = Color(0xFFE5645B)
internal val TextLow = PergaminoTenue

private val Scheme = darkColorScheme(
    primary = Runa,
    onPrimary = Color(0xFF07131F),
    primaryContainer = Color(0xFF16283A),
    onPrimaryContainer = Color(0xFFC9DEF3),
    inversePrimary = Color(0xFF44708F),
    secondary = OroTabardo,
    onSecondary = Color(0xFF1B1405),
    secondaryContainer = Color(0xFF2C2412),
    onSecondaryContainer = Color(0xFFF0DCA8),
    tertiary = Color(0xFF8FD3E8),
    background = Tinta,
    onBackground = Pergamino,
    surface = Piedra,
    onSurface = Pergamino,
    surfaceVariant = PiedraAlta,
    onSurfaceVariant = PergaminoMedio,
    surfaceContainerLowest = Tinta,
    surfaceContainerLow = Piedra,
    surfaceContainer = Piedra,
    surfaceContainerHigh = PiedraAlta,
    surfaceContainerHighest = Color(0xFF262B38),
    outline = Bisel,
    outlineVariant = Color(0xFF23212A),
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
    // Cifras de ancho fijo. Sin esto, una columna de ilvl, de oro o de nivel de
    // llave se descuadra al cambiar un dígito y la tabla entera baila.
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
    displayLarge = style(44, 48, FontWeight.Bold, -1.0, tabular = true),
    displayMedium = style(36, 40, FontWeight.Bold, -0.8, tabular = true),
    displaySmall = style(30, 36, FontWeight.Bold, -0.6, tabular = true),
    headlineLarge = style(26, 32, FontWeight.SemiBold, -0.4),
    headlineMedium = style(22, 28, FontWeight.SemiBold, -0.3, tabular = true),
    headlineSmall = style(19, 25, FontWeight.SemiBold, -0.2, tabular = true),
    titleLarge = style(17, 23, FontWeight.SemiBold, -0.1),
    titleMedium = style(15, 21, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.Medium),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 21, FontWeight.Normal, 0.1),
    bodySmall = style(13, 19, FontWeight.Normal, 0.1),
    labelLarge = style(14, 18, FontWeight.SemiBold, 0.2),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.6, tabular = true),
    labelSmall = style(11, 14, FontWeight.Medium, 0.8, tabular = true),
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
