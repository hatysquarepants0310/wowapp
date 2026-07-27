package com.azeroth.companion.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
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
import com.azeroth.companion.ui.components.LootRow
import com.azeroth.companion.ui.components.SectionCard

@Composable
fun DashboardScreen(
    onOpenChecklist: (String) -> Unit,
    onOpenRoster: () -> Unit = {},
    onOpenSeasonLoot: () -> Unit = {},
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
                Text(stringResource(R.string.dashboard_no_events))
            }
        }

        SectionCard(stringResource(R.string.weekly_reset)) {
            state.weeklyResetAt?.let {
                CountdownText(it, style = MaterialTheme.typography.headlineSmall)
            }
        }

        state.activeCharacterName?.let { name ->
            SectionCard(stringResource(R.string.dashboard_active_character)) {
                androidx.compose.material3.TextButton(onClick = onOpenRoster) { Text(stringResource(R.string.dashboard_see_roster)) }
                Text("$name${state.activeCharacterRealm?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.titleMedium)
                Text("${state.activeCharacterClass ?: ""} · ilvl ${state.activeCharacterIlvl}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val syncedAt = state.lastSyncedAt
                if (syncedAt != null) {
                    val minutes = java.time.Duration.between(syncedAt, java.time.Instant.now()).toMinutes()
                    Text(
                        if (minutes > 30) stringResource(R.string.dashboard_stale, minutes)
                        else stringResource(R.string.dashboard_synced_ago, minutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (minutes > 30) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(stringResource(R.string.dashboard_not_synced), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionCard(stringResource(R.string.great_vault)) {
            val vault = state.vault
            Spacer(Modifier.height(8.dp))
            if (vault != null) {
                // Rejilla 3x3 como la interfaz del juego: una fila por fuente,
                // tres recompensas por fila que se desbloquean por umbrales.
                VaultGridRow(stringResource(R.string.vault_row_raid), vault.raidSlots)
                VaultGridRow(stringResource(R.string.vault_row_mplus), vault.mythicPlusSlots)
                VaultGridRow(stringResource(R.string.vault_row_world), vault.worldSlots)
                Spacer(Modifier.height(6.dp))
                val unlocked = (vault.raidSlots.predictedRewardIlvl +
                    vault.mythicPlusSlots.predictedRewardIlvl +
                    vault.worldSlots.predictedRewardIlvl).filterNotNull()
                Text(
                    if (unlocked.isEmpty()) stringResource(R.string.vault_none_unlocked)
                    else stringResource(R.string.vault_unlocked, unlocked.size, unlocked.max()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                // Banda y Mythic+ llevan fecha en la API, así que son exactos.
                // Mundo no: la Bóveda no se expone y las Delves solo vienen como
                // total acumulado, de modo que hace falta una lectura anterior
                // al reset para saber cuáles son de esta semana.
                if (state.worldBaselineMissing) {
                    Text(
                        stringResource(R.string.vault_world_pending),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                ConfidenceBadge(vault.confidence)
            } else {
                Text(stringResource(R.string.vault_need_sync),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Lo exclusivo de la temporada: se pierde cuando la temporada cierra, así
        // que merece estar en Inicio y no enterrado en un submenú.
        if (state.seasonMounts.isNotEmpty()) {
            SectionCard(stringResource(R.string.season_exclusive)) {
                Text(
                    stringResource(R.string.season_exclusive_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                state.seasonMounts.take(3).forEach { mount ->
                    LootRow(mount, onClick = { onOpenSeasonLoot() })
                }
                androidx.compose.material3.TextButton(onClick = onOpenSeasonLoot) {
                    Text(stringResource(R.string.season_see_all))
                }
            }
        }
    }
}

/**
 * Una fila de la Gran Bóveda tal como se ve en el juego: tres casillas de
 * recompensa que se desbloquean al alcanzar cada umbral de actividades.
 */
@Composable
private fun VaultGridRow(label: String, slot: com.azeroth.companion.core.model.SlotProgress) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text("${slot.current}/${slot.thresholds.last()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            slot.thresholds.forEachIndexed { i, threshold ->
                val unlocked = slot.current >= threshold
                val ilvl = slot.predictedRewardIlvl.getOrNull(i)
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(
                            if (unlocked) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .border(
                            1.dp,
                            if (unlocked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (unlocked) {
                            Text("★", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Text(ilvl?.let { "ilvl $it" } ?: stringResource(R.string.vault_ready),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        } else {
                            Text("🔒", style = MaterialTheme.typography.bodyMedium)
                            Text("$threshold",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
