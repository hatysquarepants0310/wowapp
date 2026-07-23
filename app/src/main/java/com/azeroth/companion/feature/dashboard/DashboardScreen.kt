package com.azeroth.companion.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.azeroth.companion.R
import com.azeroth.companion.ui.components.ConfidenceBadge
import com.azeroth.companion.ui.components.CountdownText
import com.azeroth.companion.ui.components.SectionCard

@Composable
fun DashboardScreen(
    onOpenChecklist: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.authBroken) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    stringResource(R.string.auth_broken_banner),
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SectionCard(stringResource(R.string.next_event)) {
            if (state.nextEventStartsAt != null) {
                Text(state.nextEventName, style = MaterialTheme.typography.titleLarge)
                Text(state.nextEventZone, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                CountdownText(state.nextEventStartsAt!!)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConfidenceBadge(state.nextEventConfidence)
                    Button(onClick = { state.nextEventId?.let(onOpenChecklist) }) {
                        Text(stringResource(R.string.prepare))
                    }
                }
            } else {
                Text("Sin eventos próximos en el catálogo.")
            }
        }

        SectionCard(stringResource(R.string.weekly_reset)) {
            state.weeklyResetAt?.let {
                CountdownText(it, style = MaterialTheme.typography.headlineSmall)
            }
        }

        state.activeCharacterName?.let { name ->
            SectionCard("Personaje activo") {
                Text("$name · ${state.activeCharacterClass ?: ""} · ilvl ${state.activeCharacterIlvl}",
                    style = MaterialTheme.typography.titleMedium)
                val syncedAt = state.lastSyncedAt
                if (syncedAt != null) {
                    val minutes = java.time.Duration.between(syncedAt, java.time.Instant.now()).toMinutes()
                    Text(
                        if (minutes > 30) "⚠ Datos de hace $minutes min — pueden no reflejar acciones recientes in-game"
                        else "Sincronizado hace $minutes min",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (minutes > 30) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Aún sin sincronizar", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionCard(stringResource(R.string.great_vault)) {
            // Sin sesión de Battle.net el progreso es manual (modo degradado, §11).
            VaultRow("Banda", 0, listOf(2, 4, 6))
            VaultRow("Mythic+", 0, listOf(1, 4, 8))
            VaultRow("Mundo", 0, listOf(2, 4, 8))
            Spacer(Modifier.height(4.dp))
            ConfidenceBadge(com.azeroth.companion.core.model.Confidence.ESTIMATED)
        }

        SectionCard("Pendientes de alta prioridad") {
            state.topPending.forEach { task ->
                Text(
                    "• " + (task.title["es_MX"] ?: task.title.values.firstOrNull().orEmpty()),
                    Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun VaultRow(label: String, current: Int, thresholds: List<Int>) {
    val max = thresholds.last()
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("$label · $current/$max", style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { current.toFloat() / max },
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
