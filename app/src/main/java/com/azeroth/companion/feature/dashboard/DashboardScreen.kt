package com.azeroth.companion.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.R
import com.azeroth.companion.core.model.SlotProgress
import com.azeroth.companion.ui.components.ConfidenceBadge
import com.azeroth.companion.ui.components.CountdownText
import com.azeroth.companion.ui.components.Divider
import com.azeroth.companion.ui.components.HeroPanel
import com.azeroth.companion.ui.components.LootRow
import com.azeroth.companion.ui.components.Panel
import com.azeroth.companion.ui.components.PanelTone
import com.azeroth.companion.ui.components.Pill
import com.azeroth.companion.ui.components.Radius
import com.azeroth.companion.ui.components.Screen
import com.azeroth.companion.ui.components.SectionHeader
import com.azeroth.companion.ui.components.Spacing
import com.azeroth.companion.ui.components.StatusDot
import com.azeroth.companion.ui.theme.Gold
import com.azeroth.companion.ui.theme.Positive
import com.azeroth.companion.ui.theme.Warning
import java.time.Duration
import java.time.Instant

/**
 * Inicio.
 *
 * Una sola pregunta manda: ¿qué tengo que hacer ahora? Por eso arriba va el
 * próximo evento con su cuenta atrás —lo único que caduca— y justo debajo la
 * Gran Bóveda, que es lo que decide la semana. Lo demás va cediendo peso hacia
 * abajo. Antes todo estaba en tarjetas del mismo tamaño y no había forma de
 * saber qué mirar primero.
 */
@Composable
fun DashboardScreen(
    onOpenChecklist: (String) -> Unit,
    onOpenRoster: () -> Unit = {},
    onOpenSeasonLoot: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Screen {
        if (state.authBroken) {
            item {
                Panel(tone = PanelTone.Warning) {
                    Text(
                        stringResource(R.string.auth_broken_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        // ---- Ancla: el próximo evento ------------------------------------
        item {
            HeroPanel {
                Text(
                    stringResource(R.string.next_event).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(Spacing.sm))
                if (state.nextEventStartsAt != null) {
                    Text(
                        state.nextEventName,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.nextEventZone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    CountdownText(
                        state.nextEventStartsAt!!,
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        ConfidenceBadge(state.nextEventConfidence)
                        Pill(
                            stringResource(R.string.prepare),
                            color = MaterialTheme.colorScheme.primary,
                            filled = true,
                            modifier = Modifier.clickable {
                                state.nextEventId?.let(onOpenChecklist)
                            },
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.dashboard_no_events),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        // ---- Reset semanal + personaje, en una sola fila de datos --------
        item {
            Panel(padding = PaddingValues(Spacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.weekly_reset).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.weeklyResetAt?.let {
                            CountdownText(it, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    state.activeCharacterName?.let { name ->
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(Radius.sm))
                                .clickable(onClick = onOpenRoster)
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    state.activeCharacterClass?.let { append(it).append(" · ") }
                                    append("ilvl ").append(state.activeCharacterIlvl)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SyncFreshness(state.lastSyncedAt)
                        }
                    }
                }
            }
        }

        // ---- Gran Bóveda -------------------------------------------------
        item { SectionHeader(stringResource(R.string.great_vault)) }
        item {
            val vault = state.vault
            if (vault == null) {
                Panel {
                    Text(
                        stringResource(R.string.vault_need_sync),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Panel(padding = PaddingValues(Spacing.lg)) {
                    VaultRow(stringResource(R.string.vault_row_raid), vault.raidSlots)
                    Spacer(Modifier.height(Spacing.lg))
                    VaultRow(stringResource(R.string.vault_row_mplus), vault.mythicPlusSlots)
                    Spacer(Modifier.height(Spacing.lg))
                    VaultRow(stringResource(R.string.vault_row_world), vault.worldSlots)

                    Spacer(Modifier.height(Spacing.lg))
                    Divider()
                    Spacer(Modifier.height(Spacing.md))
                    val unlocked = (
                        vault.raidSlots.predictedRewardIlvl +
                            vault.mythicPlusSlots.predictedRewardIlvl +
                            vault.worldSlots.predictedRewardIlvl
                        ).filterNotNull()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (unlocked.isEmpty()) {
                                stringResource(R.string.vault_none_unlocked)
                            } else {
                                stringResource(R.string.vault_unlocked, unlocked.size, unlocked.max())
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (unlocked.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ConfidenceBadge(vault.confidence)
                    }
                    if (state.worldBaselineMissing) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            stringResource(R.string.vault_world_pending),
                            style = MaterialTheme.typography.bodySmall,
                            color = Warning,
                        )
                    }
                }
            }
        }

        // ---- Exclusivo de temporada --------------------------------------
        if (state.seasonMounts.isNotEmpty()) {
            item {
                SectionHeader(
                    stringResource(R.string.season_exclusive),
                    action = stringResource(R.string.season_see_all),
                    onAction = onOpenSeasonLoot,
                )
            }
            item {
                Text(
                    stringResource(R.string.season_exclusive_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(minOf(3, state.seasonMounts.size)) { index ->
                LootRow(state.seasonMounts[index], onClick = { onOpenSeasonLoot() })
            }
        }
    }
}

@Composable
private fun SyncFreshness(syncedAt: Instant?) {
    if (syncedAt == null) {
        Text(
            stringResource(R.string.dashboard_not_synced),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val minutes = Duration.between(syncedAt, Instant.now()).toMinutes()
    val stale = minutes > 30
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (stale) Warning else Positive, size = 6.dp)
        Spacer(Modifier.width(Spacing.xs))
        Text(
            if (stale) {
                stringResource(R.string.dashboard_stale, minutes)
            } else {
                stringResource(R.string.dashboard_synced_ago, minutes)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Una fila de la Bóveda tal como se ve en el juego: tres casillas que se abren
 * al llegar a cada umbral. Las cerradas no gritan: son un hueco vacío con su
 * número, y solo las abiertas llevan color, que es lo que hay que mirar.
 */
@Composable
private fun VaultRow(label: String, slot: SlotProgress) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                "${slot.current}/${slot.thresholds.lastOrNull() ?: 0}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            slot.thresholds.forEachIndexed { index, threshold ->
                val unlocked = slot.current >= threshold
                val ilvl = slot.predictedRewardIlvl.getOrNull(index)
                Box(
                    Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(
                            if (unlocked) {
                                Gold.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (unlocked) {
                            Text(
                                ilvl?.toString() ?: stringResource(R.string.vault_ready),
                                style = MaterialTheme.typography.titleMedium,
                                color = Gold,
                            )
                            Text(
                                "ilvl",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outline),
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                "$threshold",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
