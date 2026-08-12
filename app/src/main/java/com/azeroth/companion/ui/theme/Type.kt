package com.azeroth.companion.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.azeroth.companion.R

/**
 * Tipografía.
 *
 * Esto era el mayor delator y hasta ahora estaba sin tocar: la app heredaba
 * **Roboto**, la fuente de fábrica de Android. Se pueden cambiar los colores
 * tres veces, que si todo el texto está en Roboto la interfaz sigue leyéndose
 * como una app de Android cualquiera. El detector lo marca (regla `typeface`).
 *
 * Blizzard usa Friz Quadrata para la interfaz y Morpheus para títulos; las dos
 * son de licencia comercial y **no se embeben**. Las sustitutas libres, todas
 * empaquetadas en el APK (nunca por CDN):
 *
 *  - **Cinzel** — romana lapidaria, mayúsculas talladas. SOLO títulos y nombres.
 *  - **EB Garamond** — glífica legible que aguanta párrafos largos. Es el 90%
 *    del texto, que es la proporción que usa el juego.
 *  - **IBM Plex Mono** — cifras. Ancho fijo de verdad, no `tnum` sobre una
 *    proporcional.
 *
 * El error que más delata sería usar la display para todo. En el juego la
 * lapidaria aparece con cuentagotas; aquí igual.
 */
private val Display = FontFamily(
    Font(R.font.cinzel_400, FontWeight.Normal),
    Font(R.font.cinzel_600, FontWeight.SemiBold),
    Font(R.font.cinzel_700, FontWeight.Bold),
)

private val Body = FontFamily(
    Font(R.font.eb_garamond_400, FontWeight.Normal),
    Font(R.font.eb_garamond_500, FontWeight.Medium),
    Font(R.font.eb_garamond_600, FontWeight.SemiBold),
)

private val Mono = FontFamily(
    Font(R.font.ibm_plex_mono_400, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_600, FontWeight.SemiBold),
)

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    family: FontFamily,
    size: Int,
    height: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = height.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = lineHeightStyle,
    // Cifras de ancho fijo. Sin esto una columna de ilvl o de oro se descuadra
    // al cambiar un dígito y comparar —lo único que se hace en una tabla— deja
    // de funcionar.
    fontFeatureSettings = if (tabular) "tnum" else null,
)

/**
 * Estilo para columnas de cifras: monoespaciada y tabular. Se usa en las filas
 * de datos y en cualquier número que se compare con otro.
 */
val NumberStyle: TextStyle = style(Mono, 14, 20, FontWeight.SemiBold, tabular = true)

/**
 * La cifra protagonista de un bloque: el ilvl, la puntuación, el oro. También
 * monoespaciada, porque es justo la que el usuario compara entre personajes o
 * entre semanas; si baila al cambiar un dígito, la comparación se pierde.
 */
val BigNumberStyle: TextStyle = style(Mono, 26, 32, FontWeight.SemiBold, -0.3, tabular = true)

internal val AppTypography = Typography(
    // Display: la lapidaria. Nombres propios y cifras protagonistas.
    displayLarge = style(Display, 38, 44, FontWeight.Bold, -0.5, tabular = true),
    displayMedium = style(Display, 31, 36, FontWeight.Bold, -0.4, tabular = true),
    displaySmall = style(Display, 26, 32, FontWeight.Bold, -0.3, tabular = true),
    headlineLarge = style(Display, 23, 29, FontWeight.SemiBold, -0.2),
    headlineMedium = style(Display, 20, 26, FontWeight.SemiBold, -0.1),
    headlineSmall = style(Mono, 18, 24, FontWeight.SemiBold, tabular = true),

    // Títulos de fila y de sección: ya en la glífica de lectura.
    titleLarge = style(Body, 19, 25, FontWeight.SemiBold),
    titleMedium = style(Body, 17, 23, FontWeight.SemiBold),
    titleSmall = style(Body, 16, 22, FontWeight.Medium),

    // Cuerpo: el 90% del texto.
    bodyLarge = style(Body, 17, 26, FontWeight.Normal),
    bodyMedium = style(Body, 16, 23, FontWeight.Normal),
    bodySmall = style(Body, 14, 20, FontWeight.Normal),

    // Etiquetas: monoespaciada en versalita. Se leen como etiquetas de addon,
    // no como texto.
    labelLarge = style(Mono, 13, 17, FontWeight.SemiBold, 0.4, tabular = true),
    labelMedium = style(Mono, 11, 15, FontWeight.SemiBold, 0.8, tabular = true),
    labelSmall = style(Mono, 10, 14, FontWeight.Normal, 0.9, tabular = true),
)
