package com.azeroth.companion.feature.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.data.InstanceSummary

/**
 * Contenido (todo en uno): afijos de M+ de la semana, y mazmorras/bandas de
 * CUALQUIER expansión (la actual destacada, las anteriores aparte para no
 * confundir). Fuentes oficiales — Blizzard Game Data + Raider.IO.
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

        state.error?.let {
            Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        }

        when (tab) {
            0 -> AffixesTab(state)
            1 -> InstancesTab(isRaid = false, state = state, viewModel = viewModel)
            2 -> InstancesTab(isRaid = true, state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun AffixesTab(state: ContentState) {
    if (state.loading && state.affixes.isEmpty()) { Loading(); return }
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
private fun InstancesTab(isRaid: Boolean, state: ContentState, viewModel: ContentViewModel) {
    Column(Modifier.fillMaxSize()) {
        ExpansionSelector(state, viewModel)
        if (state.loading) { Loading(); return }
        val instances = if (isRaid) state.expansion?.raids else state.expansion?.dungeons
        if (instances.isNullOrEmpty()) {
            Text("Sin datos de contenido para esta expansión.",
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(instances, key = { it.id }) { instance ->
                InstanceCard(instance, state.bossesByInstance[instance.id], viewModel)
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ExpansionSelector(state: ContentState, viewModel: ContentViewModel) {
    val current = state.expansions.filter { it.isCurrent }
    val past = state.expansions.filter { !it.isCurrent }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            current.forEach { exp ->
                FilterChip(
                    selected = state.selectedExpansionId == exp.id,
                    onClick = { viewModel.selectExpansion(exp.id) },
                    label = { Text(exp.name) },
                )
            }
        }
        TextButton(onClick = { viewModel.togglePastExpansions() }) {
            Text(if (state.showPastExpansions) "Ocultar expansiones anteriores"
            else "Ver expansiones anteriores (${past.size})")
        }
        AnimatedVisibility(visible = state.showPastExpansions) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                past.forEach { exp ->
                    if (state.selectedExpansionId == exp.id) {
                        FilterChip(selected = true, onClick = {},
                            label = { Text(exp.name) })
                    } else {
                        AssistChip(onClick = { viewModel.selectExpansion(exp.id) },
                            label = { Text(exp.name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun InstanceCard(instance: InstanceSummary, bosses: List<String>?, viewModel: ContentViewModel) {
    Card(Modifier.fillMaxWidth().clickable { viewModel.loadBosses(instance.id) }) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(instance.name, style = MaterialTheme.typography.titleSmall)
                Text(if (bosses == null) "▸" else "▾",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = bosses != null) {
                Column(Modifier.padding(top = 6.dp)) {
                    if (bosses.isNullOrEmpty()) {
                        Text("Sin jefes listados o sin conexión.",
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

@Composable
private fun Loading() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { CircularProgressIndicator() }
}
