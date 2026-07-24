package com.azeroth.companion.feature.character

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.azeroth.companion.data.EquippedItem

/**
 * Personaje: elige cualquier alt de tu cuenta y ve su nivel, ilvl, equipo por
 * slot y colección de monturas. Como una armería en el bolsillo.
 */
@Composable
fun CharacterScreen(viewModel: CharacterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Center { CircularProgressIndicator() }
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
                Card(Modifier.fillMaxWidth()) {
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
            item { Center { CircularProgressIndicator() } }
        }

        state.detail?.equipment?.takeIf { it.isNotEmpty() }?.let { equipment ->
            item {
                Text("Equipo", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp))
            }
            items(equipment) { item -> EquipmentRow(item) }
        }

        state.detail?.mountNames?.takeIf { it.isNotEmpty() }?.let { mounts ->
            item {
                Text("Monturas (${mounts.size})", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp))
            }
            item {
                Text(mounts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun CharacterPicker(viewModel: CharacterViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) {
            Text(state.selected?.let { "${it.name} · ${it.realmName}" } ?: "Elegir personaje")
        }
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
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            item.iconUrl?.let { url ->
                coil.compose.AsyncImage(
                    model = url,
                    contentDescription = item.name,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(item.slot, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.name, style = MaterialTheme.typography.bodyMedium)
            }
            if (item.itemLevel > 0) {
                Text("ilvl ${item.itemLevel}", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) { content() }
}
