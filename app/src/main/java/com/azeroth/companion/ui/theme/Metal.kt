package com.azeroth.companion.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * El material de la app: **metal biselado**. Uno solo, no diez.
 *
 * `docs/UI-WARCRAFT-HERRAMIENTAS.md` §3 lo describe en CSS como un `box-shadow`
 * con varios `inset` que simulan luz arriba y sombra abajo, más una sombra
 * exterior de **desenfoque cero** para que la pieza se vea apoyada y no
 * flotando. En Compose no existe `inset`, así que se dibuja: dos filos claros
 * arriba y a la izquierda, dos oscuros abajo y a la derecha, y una línea sólida
 * bajo la pieza.
 *
 * Por qué esto y no una sombra difusa: la sombra suave con desenfoque es el
 * gesto genérico por excelencia, el que traen de fábrica todas las librerías de
 * componentes. Un objeto de metal apoyado sobre una mesa no tiene halo.
 *
 * Se eligió metal y no pergamino ni piedra porque la app es una consola de
 * datos —tablas, cifras, listas— y el pergamino a pantalla completa hace
 * ilegible el texto encima. El pergamino aparece únicamente en los mapas, que
 * son arte del propio juego.
 */

/** Grosor del filo. Un píxel lógico: el bisel se intuye, no se dibuja. */
private val EDGE = 1.dp

/**
 * Superficie de metal biselado.
 *
 * @param seated dibuja el asiento inferior (sombra sólida, desenfoque cero).
 *   Se apaga en piezas que van embutidas dentro de otra.
 */
fun Modifier.metal(
    base: Color,
    corner: Dp = 0.dp,
    seated: Boolean = true,
): Modifier = this
    .background(
        // Degradado vertical corto: la chapa recibe la luz desde arriba.
        Brush.verticalGradient(
            listOf(base.lighten(0.10f), base, base.darken(0.12f)),
        ),
    )
    .drawWithContent {
        drawContent()
        val edge = EDGE.toPx()
        // Filo iluminado: arriba y a la izquierda.
        drawRect(
            Color.White.copy(alpha = 0.16f),
            size = Size(size.width, edge),
        )
        drawRect(
            Color.White.copy(alpha = 0.07f),
            size = Size(edge, size.height),
        )
        // Filo en sombra: abajo y a la derecha.
        drawRect(
            Color.Black.copy(alpha = 0.55f),
            topLeft = Offset(0f, size.height - edge),
            size = Size(size.width, edge),
        )
        drawRect(
            Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(size.width - edge, 0f),
            size = Size(edge, size.height),
        )
    }

/**
 * Asiento: la línea sólida bajo una pieza. Sustituye a la sombra difusa y es lo
 * que hace que se lea como algo apoyado.
 */
fun Modifier.seated(color: Color = Color.Black.copy(alpha = 0.45f), depth: Dp = 2.dp): Modifier =
    this.drawBehind {
        drawRect(
            color,
            topLeft = Offset(0f, depth.toPx()),
            size = Size(size.width, size.height),
        )
    }

/**
 * Filo interior hundido: para huecos, campos de texto y casillas vacías. Es el
 * bisel al revés — la luz cae dentro, no encima.
 */
fun Modifier.inset(base: Color): Modifier = this
    .background(base.darken(0.18f))
    .drawWithContent {
        drawContent()
        val edge = EDGE.toPx()
        drawRect(Color.Black.copy(alpha = 0.5f), size = Size(size.width, edge))
        drawRect(Color.Black.copy(alpha = 0.3f), size = Size(edge, size.height))
        drawRect(
            Color.White.copy(alpha = 0.10f),
            topLeft = Offset(0f, size.height - edge),
            size = Size(size.width, edge),
        )
    }

internal fun Color.lighten(amount: Float): Color = Color(
    red = (red + amount).coerceAtMost(1f),
    green = (green + amount).coerceAtMost(1f),
    blue = (blue + amount).coerceAtMost(1f),
    alpha = alpha,
)

internal fun Color.darken(amount: Float): Color = Color(
    red = (red - amount).coerceAtLeast(0f),
    green = (green - amount).coerceAtLeast(0f),
    blue = (blue - amount).coerceAtLeast(0f),
    alpha = alpha,
)
