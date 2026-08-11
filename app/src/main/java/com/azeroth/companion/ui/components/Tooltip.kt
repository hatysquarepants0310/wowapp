package com.azeroth.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.azeroth.companion.ui.theme.Bisel
import com.azeroth.companion.ui.theme.OroTabardo
import com.azeroth.companion.ui.theme.Pergamino
import com.azeroth.companion.ui.theme.PergaminoMedio
import com.azeroth.companion.ui.theme.Positive
import com.azeroth.companion.ui.theme.Tinta

/**
 * El elemento firma de la app: el tooltip de World of Warcraft.
 *
 * Es el objeto más reconocible del juego, más que cualquier botón, y aquí es
 * la unidad de contenido en todas partes: botín, objetos de la casa de
 * subastas, detalle de misión, recompensas de temporada.
 *
 * Se replica de verdad, no "parecido":
 *  - fondo casi negro azulado, no el gris de una tarjeta cualquiera,
 *  - borde fino de 1px en oro apagado, sin sombra difusa,
 *  - el título va en el COLOR DE LA CALIDAD del objeto,
 *  - las líneas de estadística se alinean etiqueta-izquierda / valor-derecha,
 *  - lo verde es efecto de uso, tal y como lo pinta el juego,
 *  - el precio, si lo hay, cierra abajo en oro/plata/cobre.
 *
 * Si se quita este componente, la app deja de parecer de WoW: esa es la prueba
 * de que es el elemento firma y no un adorno.
 */
@Composable
fun Tooltip(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = Pergamino,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(Tinta)
            .border(1.dp, Bisel, RoundedCornerShape(Radius.sm))
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
                        color = PergaminoMedio,
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
    valueColor: Color = Pergamino,
) {
    Row(
        modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = PergaminoMedio)
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
            Coin("%,d".format(gold), OroTabardo)
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
        Text(amount, style = MaterialTheme.typography.bodySmall, color = Pergamino)
        Text(
            when (color) {
                OroTabardo -> "o"
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
            .background(Bisel),
    )
}
