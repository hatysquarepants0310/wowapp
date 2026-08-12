package com.azeroth.companion.feature.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azeroth.companion.ui.theme.metal
import com.azeroth.companion.ui.theme.Surface
import com.azeroth.companion.ui.components.Radius
import com.azeroth.companion.ui.components.Spacing

/**
 * Una entrada de un hub: a dónde lleva y para qué sirve.
 *
 * La app tenía tres pestañas —Inicio, Personaje y Más— y "Más" era un cajón de
 * sastre con doce entradas planas: todo lo que no cabía en las otras dos
 * acababa ahí, así que para llegar a cualquier cosa había que leerse la lista
 * entera. Ahora cada pestaña tiene un tema y, si agrupa varias pantallas, lo
 * hace con este componente y con una frase que dice qué vas a encontrar.
 */
data class HubEntry(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

/** Rejilla de accesos de un hub: dos por fila, tocables de sobra. */
fun LazyListScope.hubGrid(entries: List<HubEntry>, onNavigate: (String) -> Unit) {
    val rows = entries.chunked(2)
    items(rows.size) { index ->
        val row = rows[index]
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            row.forEach { entry ->
                HubCard(entry, Modifier.weight(1f)) { onNavigate(entry.route) }
            }
            // Fila impar: el hueco se rellena para que la última tarjeta no
            // ocupe el ancho entero y rompa la retícula.
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HubCard(entry: HubEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    // Chapa de metal, igual que el resto: era un rectángulo de color plano con
    // esquina blanda, o sea la tarjeta por defecto de cualquier librería.
    Column(
        modifier
            .metal(Surface)
            .clickable(onClick = onClick)
            .padding(Spacing.md)
            .height(112.dp),
    ) {
        Box(
            Modifier.size(34.dp).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(entry.icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            entry.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            entry.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Fila ancha para el acceso principal de un hub. */
@Composable
fun HubFeature(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    val color = accent ?: MaterialTheme.colorScheme.primary
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.none))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.none))
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
