package com.azeroth.companion.feature.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.data.InstanceSummary

/**
 * Contenido (todo en uno): afijos de M+ de la semana, mazmorras y bandas de la
 * expansión con sus jefes. Fuentes oficiales (Blizzard Game Data + Raider.IO),
 * sin depender de Wowhead ni de ningún scraper.
 */
@Composable
fun ContentScreen(viewModel: ContentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Mythic+", "Mazmorras", "Bandas")

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }

        if (state.loading) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return
        }

        state.error?.let {
            Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        }

        when (tab) {
            0 -> AffixesTab(state)
            1 -> InstancesTab(state.expansion?.dungeons ?: emptyList(), state, viewModel)
            2 -> InstancesTab(state.expansion?.raids ?: emptyList(), state, viewModel)
        }
    }
}

@Composable
private fun AffixesTab(state: ContentState) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Afijos de la semana", style = MaterialTheme.typography.titleMedium)
            if (state.affixTitle.isNotBlank()) {
                Text(state.affixTitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
        }
        items(state.affixes) { affix ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(affix.name, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary)
                    Text(affix.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text("Fuente: Raider.IO · se actualiza cada reset semanal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun InstancesTab(
    instances: List<InstanceSummary>,
    state: ContentState,
    viewModel: ContentViewModel,
) {
    if (instances.isEmpty()) {
        Text("Sin datos de contenido. Reintenta con conexión.",
            Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    state.expansion?.let { exp ->
        Text(exp.name, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(instances, key = { it.id }) { instance ->
            val bosses = state.bossesByInstance[instance.id]
            Card(
                Modifier.fillMaxWidth().clickable { viewModel.loadBosses(instance.id) },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(instance.name, style = MaterialTheme.typography.titleSmall)
                        Text(if (bosses == null) "▸" else "▾",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedVisibility(visible = bosses != null) {
                        Column(Modifier.padding(top = 6.dp)) {
                            if (bosses.isNullOrEmpty()) {
                                Text("Toca de nuevo o revisa tu conexión.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                bosses.forEachIndexed { i, boss ->
                                    Text("${i + 1}. $boss", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
