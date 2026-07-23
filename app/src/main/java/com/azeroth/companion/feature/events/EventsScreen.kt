package com.azeroth.companion.feature.events

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.R
import com.azeroth.companion.ui.components.ConfidenceBadge
import com.azeroth.companion.ui.components.CountdownText

@Composable
fun EventsScreen(
    onOpenDetail: (String) -> Unit,
    viewModel: EventsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.rows, key = { it.definition.id }) { row ->
            Card(Modifier.fillMaxWidth().clickable { onOpenDetail(row.definition.id) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        row.definition.name["es_MX"] ?: row.definition.name.values.first(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(row.definition.zone, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    row.next?.let { occ ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CountdownText(occ.startsAt, style = MaterialTheme.typography.titleLarge)
                            ConfidenceBadge(occ.confidence)
                        }
                    } ?: Text("Sin próxima ocurrencia calculable.")
                }
            }
        }
    }
}

/** Detalle + checklist previa (§5.4, §9.2). */
@Composable
fun EventDetailScreen(
    eventId: String,
    viewModel: EventsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val def = state.rows.firstOrNull { it.definition.id == eventId }?.definition ?: return
    val checks = remember(eventId) { mutableStateOf(setOf<Int>()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(def.name["es_MX"] ?: def.name.values.first(), style = MaterialTheme.typography.headlineSmall)
        def.location["es_MX"]?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        def.coordinates?.let { Text("(${it.x}, ${it.y})", style = MaterialTheme.typography.bodySmall) }

        Text("Checklist previa", style = MaterialTheme.typography.titleMedium)
        def.preconditions.forEachIndexed { i, hint ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = i in checks.value,
                    onCheckedChange = { on ->
                        checks.value = if (on) checks.value + i else checks.value - i
                    },
                )
                Text(hint.text["es_MX"] ?: hint.text.values.first(),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (def.phases.isNotEmpty()) {
            Text("Fases", style = MaterialTheme.typography.titleMedium)
            def.phases.sortedBy { it.order }.forEach { phase ->
                Text(
                    "${phase.order}. ${phase.name["es_MX"] ?: phase.name.values.first()}" +
                        (phase.durationSeconds?.let { " (${it / 60} min)" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                phase.playerActionHint["es_MX"]?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                }
            }
        }

        def.knownIssues.forEach { issue ->
            Text("⚠ " + (issue["es_MX"] ?: issue.values.first()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary)
        }

        OutlinedButton(onClick = { viewModel.markEventJustStarted(def.id) }) {
            Text(stringResource(R.string.event_just_started))
        }
        state.calibrationMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}
