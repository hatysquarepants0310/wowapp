package com.azeroth.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azeroth.companion.ui.theme.Gold
import com.azeroth.companion.ui.theme.Line
import com.azeroth.companion.ui.theme.Positive
import com.azeroth.companion.ui.theme.Surface
import com.azeroth.companion.ui.theme.TextHigh
import com.azeroth.companion.ui.theme.TextMid

/**
 * Tarjeta de objeto.
 *
 * Antes era una réplica del tooltip del juego, con borde dorado y todo. Se
 * quita la filigrana: en un móvil de 390px ese trim no se lee como épico, se
 * lee como recargado. Lo que SÍ se conserva es lo único que de verdad importa
 * de un tooltip —el nombre en el color de la calidad, las estadísticas
 * alineadas, los efectos en verde y el precio en oro/plata/cobre—, porque eso
 * es vocabulario que el jugador lee sin leer.
 *
 * La identidad de la app no la pone el marco: la ponen el arte del juego, el
 * render de tu personaje y el color de tu clase.
 */
@Composable
fun Tooltip(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = TextHigh,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(Surface)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
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
 * Una línea de estadística del tooltip: etiqueta a la izquierda, valor a la
 * derecha. Es lo que permite comparar dos objetos de un vistazo.
 */
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
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMid)
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor)
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

/** Pie de precio en oro/plata/cobre, con los colores del juego. */
@Composable
fun TooltipPrice(copper: Long, modifier: Modifier = Modifier) {
    val gold = copper / 10_000
    val silver = (copper % 10_000) / 100
    val rest = copper % 100
    Row(modifier.padding(top = Spacing.xs)) {
        if (gold > 0) {
            Coin("%,d".format(gold), Gold)
            Spacer(Modifier.width(Spacing.xs))
        }
        if (gold > 0 || silver > 0) {
            Coin(silver.toString(), Color(0xFFC7C7C7))
            Spacer(Modifier.width(Spacing.xs))
        }
        Coin(rest.toString(), Color(0xFFB87333))
    }
}

@Composable
private fun Coin(amount: String, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(amount, style = MaterialTheme.typography.bodySmall, color = TextHigh)
        Text(
            when (color) {
                Gold -> "o"
                Color(0xFFC7C7C7) -> "p"
                else -> "c"
            },
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/** Separador interno del tooltip, como la línea que parte las secciones. */
@Composable
fun TooltipDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(vertical = 0.dp)
            .background(Line),
    )
}
