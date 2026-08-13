package com.azeroth.companion.feature.character

import androidx.compose.foundation.layout.PaddingValues
import com.azeroth.companion.ui.theme.QualityColors
import com.azeroth.companion.ui.components.GameIcon
import com.azeroth.companion.ui.components.Panel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.ui.components.WowButton
import com.azeroth.companion.ui.components.WowLoading
import com.azeroth.companion.data.EquippedItem

/**
 * Personaje: elige cualquier alt de tu cuenta y ve su nivel, ilvl, equipo por
 * slot y colección de monturas. Como una armería en el bolsillo.
 */
@Composable
fun CharacterScreen(
    viewModel: CharacterViewModel = hiltViewModel(),
    /**
     * Contenido opcional al final. Lo usa la pestaña Personaje para colgar sus
     * enlaces hermanos sin necesidad de envolver esta pantalla en un menú.
     */
    footer: (@Composable () -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Center { WowLoading() }
        return
    }
    if (state.roster.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Sin personajes", style = MaterialTheme.typography.titleMedium)
            Text("Inicia sesión con Battle.net en Ajustes para importar tu cuenta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { CharacterPicker(viewModel) }
        item {
            state.selected?.let { c ->
                Panel(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(c.name, style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text(c.realmName, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        StatRow("Nivel", c.level.toString())
                        StatRow("Clase", c.playableClass + (c.activeSpec?.let { " · $it" } ?: ""))
                        StatRow("Facción", when (c.faction) {
                            "HORDE" -> "Horda"; "ALLIANCE" -> "Alianza"; else -> "Neutral"
                        })
                        StatRow("Nivel de objeto equipado", c.equippedItemLevel.toString())
                        StatRow("Nivel de objeto medio", c.averageItemLevel.toString())
                        state.detail?.let { StatRow("Monturas", it.mountCount.toString()) }
                    }
                }
            }
        }

        if (state.detailLoading) {
            item { Center { WowLoading() } }
        }

        state.detail?.equipment?.takeIf { it.isNotEmpty() }?.let { equipment ->
            item {
                Text("Equipo", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp))
            }
            items(equipment) { item -> EquipmentRow(item) }
        }

        state.detail?.mounts?.takeIf { it.isNotEmpty() }?.let { mounts ->
            item {
                Text("Monturas (${mounts.size})", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp))
            }
            // Cuadrícula con la imagen real de cada montura (3 por fila).
            items(mounts.chunked(3)) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { mount -> MountCell(mount, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        if (state.detail != null && state.detail?.equipment.isNullOrEmpty()) {
            item {
                Text("El equipo aparece tras sincronizar con sesión activa. " +
                    "Prueba \"Sincronizar ahora\" en Ajustes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (footer != null) {
            item { footer() }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun CharacterPicker(viewModel: CharacterViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    Column {
        WowButton(
            state.selected?.let { "${it.name} · ${it.realmName}" } ?: "Elegir personaje",
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.roster.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.name} · ${c.realmName} (nvl ${c.level})") },
                    onClick = { viewModel.select(c); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EquipmentRow(item: EquippedItem) {
    Panel(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            // El color de calidad ya venía en el modelo y no se usaba para
            // nada. Es la información que un jugador lee primero de una pieza:
            // el marco morado dice "épico" antes que la palabra. Ahora tiñe el
            // borde del icono y el nombre, como en el juego.
            val quality = qualityColor(item.quality)
            GameIcon(item.iconUrl, size = 44.dp, border = quality, contentDescription = item.name)
            Column(Modifier.weight(1f)) {
                Text(item.slot, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = quality,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (item.itemLevel > 0) {
                Text(
                    item.itemLevel.toString(),
                    style = com.azeroth.companion.ui.theme.NumberStyle,
                    color = com.azeroth.companion.ui.theme.Gold,
                )
            }
        }
    }
}

/**
 * Los nombres de calidad que devuelve Blizzard, a su color oficial exacto.
 * Llegan en inglés en mayúsculas (`EPIC`, `LEGENDARY`…) independientemente del
 * idioma del personaje, porque es el campo `type` y no el traducido.
 */
private fun qualityColor(quality: String): androidx.compose.ui.graphics.Color =
    when (quality.uppercase()) {
        "POOR" -> QualityColors.Poor
        "COMMON" -> QualityColors.Common
        "UNCOMMON" -> QualityColors.Uncommon
        "RARE" -> QualityColors.Rare
        "EPIC" -> QualityColors.Epic
        "LEGENDARY" -> QualityColors.Legendary
        "ARTIFACT" -> QualityColors.Artifact
        "HEIRLOOM" -> QualityColors.Heirloom
        else -> QualityColors.Common
    }

@Composable
private fun MountCell(mount: com.azeroth.companion.data.MountEntry, modifier: Modifier = Modifier) {
    Panel(modifier, padding = PaddingValues(0.dp)) {
        Column(Modifier.padding(6.dp)) {
            mount.imageUrl?.let { url ->
                coil.compose.AsyncImage(
                    model = url,
                    contentDescription = mount.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp),
                )
            }
            Text(
                mount.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) { content() }
}
