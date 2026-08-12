package com.azeroth.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azeroth.companion.ui.theme.Gold
import com.azeroth.companion.ui.theme.NumberStyle
import com.azeroth.companion.ui.theme.Positive
import com.azeroth.companion.ui.theme.TextHigh
import com.azeroth.companion.ui.theme.TextMid

/**
 * # El tooltip. El elemento firma de la app.
 *
 * `docs/UI-WARCRAFT.md` §7 pide **un** elemento reconocible y pide que sea
 * exacto, no aproximado: si es un tooltip, que sea un tooltip de verdad.
 *
 * La versión anterior de este archivo hacía justo lo contrario, y lo dejaba
 * escrito en su propio comentario: *"antes era una réplica del tooltip del
 * juego… se quita la filigrana"*. Quitarla fue el error. Un jugador de WoW ha
 * mirado el tooltip de objeto cientos de miles de veces; es la pieza de interfaz
 * que mejor conoce del mundo. Cuando la ve **casi** bien no piensa "qué limpio",
 * piensa "esto no lo ha hecho un jugador". Ese es justo el síntoma a curar, y
 * aproximarse es peor que no intentarlo.
 *
 * Así que va exacto. Lo que hace que un tooltip sea EL tooltip, en orden:
 *
 *  1. **El marco toma el color de la calidad del objeto.** No es adorno, es
 *     información: un marco morado dice "épico" antes de leer una letra.
 *  2. **Fondo negro casi opaco**, no gris de superficie. En el juego el tooltip
 *     se dibuja sobre el mundo y tiene que tapar.
 *  3. **El nombre en el color de la calidad**, arriba del todo.
 *  4. **La línea de dos columnas**: ranura a la izquierda, tipo de armadura a la
 *     derecha, en la MISMA línea. Este es el detalle que nadie copia y el que
 *     más delata: fuera de WoW no existe.
 *  5. **Nivel de objeto en dorado**, encima de las estadísticas.
 *  6. Estadísticas en cifra tabular, efectos en verde, precio abajo en
 *     oro/plata/cobre con sus tres colores.
 *
 * Sobre el marco: el documento prohíbe montarlo con nueve cajas anidadas —el
 * equivalente en Compose de nueve `div`— y pide una sola pieza. Aquí es un único
 * `drawBehind`. Una capa de dibujo, cero nodos de layout.
 */

/** Grosor de cada filo del marco. 2dp: se ve en un móvil sin comerse el ancho. */
private val FRAME = 2.dp

/**
 * Marco de tooltip dibujado en una sola capa.
 *
 * El del juego tiene tres filos —negro fuera, color de calidad en medio, negro
 * dentro—, y es eso lo que le da aspecto de placa en vez de borde de CSS.
 */
private fun Modifier.tooltipFrame(quality: Color): Modifier = this.drawBehind {
    val f = FRAME.toPx()
    val w = size.width
    val h = size.height
    // Fondo: negro casi opaco. El tooltip tapa lo que hay debajo.
    drawRect(Color(0xF2050609))
    // Filo de color de calidad, por dentro del borde exterior.
    drawRect(quality, topLeft = Offset(f, f), size = Size(w - 2 * f, f))
    drawRect(quality, topLeft = Offset(f, h - 2 * f), size = Size(w - 2 * f, f))
    drawRect(quality, topLeft = Offset(f, f), size = Size(f, h - 2 * f))
    drawRect(quality, topLeft = Offset(w - 2 * f, f), size = Size(f, h - 2 * f))
    // Filo exterior negro: separa el marco de lo que haya detrás.
    drawRect(Color.Black, size = Size(w, f))
    drawRect(Color.Black, topLeft = Offset(0f, h - f), size = Size(w, f))
    drawRect(Color.Black, size = Size(f, h))
    drawRect(Color.Black, topLeft = Offset(w - f, 0f), size = Size(f, h))
}

@Composable
fun Tooltip(
    title: String,
    modifier: Modifier = Modifier,
    /**
     * Color de calidad. Manda en el marco y en el nombre, igual que en el juego.
     * Los valores exactos están en [com.azeroth.companion.ui.theme.QualityColors].
     */
    titleColor: Color = TextHigh,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .tooltipFrame(titleColor)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            // El relleno arranca donde acaba el marco de 2+2dp.
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(Spacing.md))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    // Un nombre de objeto o de personaje puede ser larguísimo y
                    // sin espacios; sin esto empuja el contenedor y desplaza la
                    // pantalla entera de lado.
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                    )
                }
            }
        }
        content()
    }
}

/**
 * **La línea de dos columnas**: ranura a la izquierda, tipo de armadura a la
 * derecha, a la misma altura.
 *
 * Es el rasgo del tooltip de WoW que ninguna interfaz genérica tiene, y por eso
 * es el que más lo identifica. Cualquier app pone un título y una lista debajo;
 * solo esta pone "Pecho" y "Placas" en extremos opuestos de la misma línea.
 */
@Composable
fun TooltipSlotLine(
    slot: String,
    kind: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            slot,
            style = MaterialTheme.typography.bodySmall,
            color = TextHigh,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (kind != null) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                kind,
                style = MaterialTheme.typography.bodySmall,
                color = TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Nivel de objeto. En el juego va en dorado justo debajo del nombre; es la cifra
 * que la gente busca primero, así que va tabular para poder compararla de un
 * objeto a otro sin contar dígitos.
 */
@Composable
fun TooltipItemLevel(level: Int, modifier: Modifier = Modifier) {
    Text(
        "Nivel de objeto $level",
        style = NumberStyle,
        color = Gold,
        modifier = modifier.padding(top = 2.dp),
    )
}

/** Una línea de estadística: etiqueta a la izquierda, cifra tabular a la derecha. */
@Composable
fun TooltipStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextHigh,
) {
    Row(
        modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(value, style = NumberStyle, color = valueColor, maxLines = 1)
    }
}

/** Línea de efecto de uso. En el juego siempre va en verde. */
@Composable
fun TooltipEffect(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Positive,
        modifier = modifier.padding(top = 2.dp),
    )
}

/** Pie de precio en oro/plata/cobre, con los tres colores del juego. */
@Composable
fun TooltipPrice(copper: Long, modifier: Modifier = Modifier) {
    val gold = copper / 10_000
    val silver = (copper % 10_000) / 100
    val rest = copper % 100
    Row(modifier.padding(top = Spacing.xs)) {
        if (gold > 0) {
            Coin("%,d".format(gold), Gold, "o")
            Spacer(Modifier.width(Spacing.xs))
        }
        if (gold > 0 || silver > 0) {
            Coin(silver.toString(), Silver, "p")
            Spacer(Modifier.width(Spacing.xs))
        }
        Coin(rest.toString(), Copper, "c")
    }
}

private val Silver = Color(0xFFC7C7C7)
private val Copper = Color(0xFFB87333)

@Composable
private fun Coin(amount: String, color: Color, suffix: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(amount, style = NumberStyle, color = TextHigh)
        Text(suffix, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/**
 * Separador interno. En el juego es una línea muy tenue que parte las secciones;
 * aquí toma el color de la calidad al 25% para que el marco y el interior se
 * lean como una sola pieza.
 */
@Composable
fun TooltipDivider(modifier: Modifier = Modifier, quality: Color = TextMid) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .height(1.dp)
            .background(quality.copy(alpha = 0.25f)),
    )
}
