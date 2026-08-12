package com.azeroth.companion.feature.events

import androidx.compose.foundation.layout.PaddingValues
import com.azeroth.companion.ui.components.Panel
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.ui.components.WowButton
import com.azeroth.companion.R
import com.azeroth.companion.core.model.EventCadence
import com.azeroth.companion.core.model.WorldEventDefinition
import com.azeroth.companion.ui.components.ConfidenceBadge
import com.azeroth.companion.ui.components.CountdownText
import java.time.format.TextStyle
import java.util.Locale

/** Cadencia legible en español para saber "cada cuánto" ocurre el evento. */
private fun cadenceLabel(cadence: EventCadence): String = when (cadence) {
    is EventCadence.FixedInterval -> when {
        cadence.intervalMinutes % 60 == 0 -> "Cada ${cadence.intervalMinutes / 60} h"
        else -> "Cada ${cadence.intervalMinutes} min"
    }
    is EventCadence.WeeklySchedule -> "Semanal: " + cadence.entries.joinToString(", ") {
        it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es")) + " " + it.time
    }
    is EventCadence.RefreshWindows -> "Rotación: " + cadence.daysOfWeek.joinToString(", ") {
        it.getDisplayName(TextStyle.FULL, Locale("es"))
    }
    is EventCadence.Continuous -> "Continuo (${cadence.minDurationHours}–${cadence.maxDurationHours} h)"
}

private fun localized(map: Map<String, String>): String =
    map["es_MX"] ?: map.values.firstOrNull().orEmpty()

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
            Panel(Modifier.fillMaxWidth().clickable { onOpenDetail(row.definition.id) }, padding = PaddingValues(0.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(localized(row.definition.name), style = MaterialTheme.typography.titleMedium)
                    Text("${row.definition.zone} · ${cadenceLabel(row.definition.cadence)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    row.next?.let { occ ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CountdownText(occ.startsAt, style = MaterialTheme.typography.titleLarge)
                            ConfidenceBadge(occ.confidence)
                        }
                    } ?: Text("Sin próxima ocurrencia calculable.")
                    row.definition.rewardSummary.takeIf { it.isNotEmpty() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("🎁 " + localized(it), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** Detalle informativo del evento (sin checklist manual): requisitos, cadencia, recompensas, fases. */
@Composable
fun EventDetailScreen(
    eventId: String,
    viewModel: EventsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val def: WorldEventDefinition = state.rows.firstOrNull { it.definition.id == eventId }?.definition ?: return

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(localized(def.name), style = MaterialTheme.typography.headlineSmall)
        def.location["es_MX"]?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        def.coordinates?.let { Text("(${it.x}, ${it.y})", style = MaterialTheme.typography.bodySmall) }
        Text("⏱ ${cadenceLabel(def.cadence)}", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
        if (def.maxWeeklyCompletions > 1) {
            Text("Hasta ${def.maxWeeklyCompletions} completaciones con cofre por semana",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        def.rewardSummary.takeIf { it.isNotEmpty() }?.let {
            SectionHeader("Recompensas")
            Text(localized(it), style = MaterialTheme.typography.bodyMedium)
        }

        if (def.preconditions.isNotEmpty()) {
            SectionHeader("Requisitos")
            def.preconditions.forEach { hint ->
                Text("• " + localized(hint.text), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (def.phases.isNotEmpty()) {
            SectionHeader("Fases")
            def.phases.sortedBy { it.order }.forEach { phase ->
                Text(
                    "${phase.order}. ${localized(phase.name)}" +
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
            Text("⚠ " + localized(issue), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary)
        }

        Spacer(Modifier.height(4.dp))
        WowButton(
            stringResource(R.string.event_just_started),
            onClick = { viewModel.markEventJustStarted(def.id) },
        )
        Text("Úsalo para calibrar el horario si tu reino difiere del previsto.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.calibrationMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 6.dp))
}
