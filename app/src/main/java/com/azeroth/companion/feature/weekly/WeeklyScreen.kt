package com.azeroth.companion.feature.weekly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.core.model.TaskCategory
import com.azeroth.companion.ui.components.ConfidenceBadge

private val categoryLabels = mapOf(
    TaskCategory.WORLD_EVENT to "Actividades",
    TaskCategory.WEEKLY_QUEST to "Semanales",
    TaskCategory.OMNIUM_FOLIO to "Folio Omnium",
    TaskCategory.PVP to "PvP",
    TaskCategory.GREAT_VAULT to "Gran Bóveda",
    TaskCategory.CURRENCY to "Monedas",
    TaskCategory.PROFESSION to "Profesión",
    TaskCategory.DELVE to "Profundidades",
    TaskCategory.PREY_HUNT to "Sistema de presas",
    TaskCategory.WORLD_BOSS to "Jefe de mundo",
    TaskCategory.CAMPAIGN to "Historia (una vez)",
    TaskCategory.CUSTOM to "Tareas personalizadas",
    TaskCategory.LEGACY to "Legacy",
    TaskCategory.SEASONAL_REWARD to "Temporada",
)

/**
 * Checklist semanal 100% automática: el estado sale de la detección sobre los
 * snapshots del sync con Battle.net (§6). Sin entrada manual — solo lectura,
 * con badge de confianza en cada dato inferido.
 */
@Composable
fun WeeklyScreen(viewModel: WeeklyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text(
                "Detección automática desde tu cuenta de Battle.net. " +
                    "Los datos se actualizan con cada sincronización.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.groups.forEach { (category, rows) ->
            val done = rows.sumOf { it.state?.completions ?: 0 }
            val total = rows.sumOf { it.task.maxCompletions }
            item(key = "header_$category") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(categoryLabels[category] ?: category.name,
                        style = MaterialTheme.typography.titleMedium)
                    Text("$done/$total", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            items(rows, key = { it.task.id }) { row ->
                val completions = row.state?.completions ?: 0
                val complete = completions >= row.task.maxCompletions
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (complete) "☑" else "☐",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (complete) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(row.task.title["es_MX"] ?: row.task.title.values.first())
                        row.task.description["es_MX"]?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("$completions/${row.task.maxCompletions}",
                        style = MaterialTheme.typography.bodySmall)
                    row.state?.let { ConfidenceBadge(it.confidence) }
                }
            }
        }
    }
}
