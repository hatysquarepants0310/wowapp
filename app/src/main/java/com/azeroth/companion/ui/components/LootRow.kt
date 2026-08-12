package com.azeroth.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.azeroth.companion.data.LootEntry

/** Colores de calidad de WoW: el jugador reconoce un épico por el morado. */
fun qualityColor(quality: String): Color = when (quality.uppercase()) {
    "POOR" -> Color(0xFF9D9D9D)
    "COMMON" -> Color(0xFFF0F0F0)
    "UNCOMMON" -> Color(0xFF1EFF00)
    "RARE" -> Color(0xFF0070DD)
    "EPIC" -> Color(0xFFA335EE)
    "LEGENDARY" -> Color(0xFFFF8000)
    "ARTIFACT" -> Color(0xFFE6CC80)
    "HEIRLOOM" -> Color(0xFF00CCFF)
    else -> Color(0xFFC8C8C8)
}

private fun slotLabel(slot: String?): String? = when (slot?.uppercase()) {
    null -> null
    "HEAD" -> "Cabeza"; "NECK" -> "Cuello"; "SHOULDER" -> "Hombros"
    "CHEST", "ROBE" -> "Pecho"; "WAIST" -> "Cintura"; "LEGS" -> "Piernas"
    "FEET" -> "Pies"; "WRIST" -> "Muñecas"; "HAND" -> "Manos"
    "FINGER" -> "Anillo"; "TRINKET" -> "Abalorio"; "CLOAK", "BACK" -> "Espalda"
    "WEAPON", "WEAPONMAINHAND" -> "Arma"; "TWOHWEAPON" -> "Arma a dos manos"
    "WEAPONOFFHAND", "HOLDABLE", "SHIELD" -> "Mano izquierda"
    "RANGED", "RANGEDRIGHT" -> "A distancia"
    else -> slot
}

/**
 * Una línea de botín: imagen del objeto, nombre con su color de calidad,
 * de dónde sale y la probabilidad con su explicación. Blizzard no publica tasas
 * de caída, así que el número siempre viene acompañado de cómo se ha obtenido.
 */
@Composable
fun LootRow(
    entry: LootEntry,
    showSource: Boolean = true,
    onClick: ((LootEntry) -> Unit)? = null,
) {
    // El botín se presenta con el elemento firma de la app: el tooltip del
    // juego. Fondo casi negro, borde de trim y el nombre en el color de la
    // calidad, que es como el jugador lo lee sin tener que leerlo.
    val border = qualityColor(entry.quality)
    Tooltip(
        title = entry.name,
        titleColor = border,
        onClick = onClick?.let { { it(entry) } },
    ) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, border.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.iconUrl != null) {
                AsyncImage(
                    model = entry.iconUrl,
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp),
                )
            } else {
                Text(if (entry.isMount) "🐎" else "🎁")
            }
        }
        Column(Modifier.weight(1f)) {
            if (entry.owned) {
                TooltipEffect("✓ ya la tienes")
            }
            val bits = buildList {
                slotLabel(entry.slot)?.let { add(it) }
                if (showSource) {
                    add("${entry.boss} · ${entry.instance}")
                }
            }
            if (bits.isNotEmpty()) {
                Text(
                    bits.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    entry.chancePercent?.let { formatPercent(it) } ?: "probabilidad desconocida",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                ConfidenceBadge(entry.chance.confidence)
            }
            Text(
                entry.chanceExplanation,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.attempts > 0 && !entry.owned) {
                Text(
                    buildString {
                        append("Llevas ${entry.attempts} intento")
                        if (entry.attempts != 1) append("s")
                        entry.cumulativePercent?.let {
                            append(" · a estas alturas la tendría el ${formatPercent(it)}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (onClick != null && entry.bossId != 0) {
                Text(
                    "Ver dónde se consigue →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    }
}

private fun formatPercent(value: Double): String = when {
    value >= 10 -> "${value.toInt()} %"
    value >= 1 -> String.format("%.1f %%", value)
    else -> String.format("%.2f %%", value)
}
