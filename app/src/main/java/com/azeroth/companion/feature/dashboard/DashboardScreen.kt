package com.azeroth.companion.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.R
import com.azeroth.companion.core.detection.ThisWeek
import com.azeroth.companion.core.model.TrackedTask
import com.azeroth.companion.core.model.WeekTrust
import com.azeroth.companion.ui.components.CharacterHero
import com.azeroth.companion.ui.components.CountdownText
import com.azeroth.companion.ui.components.ListRow
import com.azeroth.companion.ui.components.WeekTrustBadge
import com.azeroth.companion.ui.components.LootRow
import com.azeroth.companion.ui.components.Panel
import com.azeroth.companion.ui.components.PanelTone
import com.azeroth.companion.ui.components.Screen
import com.azeroth.companion.ui.components.SectionHeader
import com.azeroth.companion.ui.components.Spacing
import com.azeroth.companion.ui.components.StatusDot
import com.azeroth.companion.ui.components.formatDuration
import com.azeroth.companion.ui.theme.Positive
import com.azeroth.companion.ui.theme.TextHigh
import com.azeroth.companion.ui.theme.Warning
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Hoy: Operate + Monitor. El personaje preside, el reloj del próximo evento
 * vive en el héroe, y la semana es una línea + una lista de tareas. Nada de
 * tres StatTiles ni de un panel de countdown debajo del retrato.
 */
private fun LazyListScope.gutterItem(content: @Composable () -> Unit) = item {
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter)) { content() }
}

@Composable
fun DashboardScreen(
    onOpenChecklist: (String) -> Unit,
    onOpenRoster: () -> Unit = {},
    onOpenSeasonLoot: () -> Unit = {},
    onOpenWeekly: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val localeKey = if (Locale.getDefault().language.startsWith("es")) "es_MX" else "en_US"

    Screen(
        contentPadding = PaddingValues(top = 0.dp, bottom = Spacing.xxl),
    ) {
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

        val activeName = state.activeCharacterName
        if (activeName != null) {
            item {
                CharacterHero(
                    name = activeName,
                    realm = state.activeCharacterRealm,
                    className = state.activeCharacterClass,
                    spec = state.activeCharacterSpec,
                    itemLevel = state.activeCharacterIlvl,
                    renderUrl = state.activeCharacterRender,
                    onClick = onOpenRoster,
                    trailing = { SyncFreshness(state.lastSyncedAt) },
                    overlay = {
                        if (state.nextEventStartsAt != null) {
                            NextEventOverlay(
                                name = state.nextEventName,
                                zone = state.nextEventZone,
                                startsAt = state.nextEventStartsAt,
                                onPrepare = state.nextEventId?.let { id -> { onOpenChecklist(id) } },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(Spacing.lg)
                                    .fillMaxWidth(0.48f),
                            )
                        }
                    },
                )
            }
        } else if (state.nextEventStartsAt != null) {
            gutterItem {
                NextEventOverlay(
                    name = state.nextEventName,
                    zone = state.nextEventZone,
                    startsAt = state.nextEventStartsAt,
                    onPrepare = state.nextEventId?.let { id -> { onOpenChecklist(id) } },
                )
            }
        }

        gutterItem {
            SectionHeader(
                stringResource(R.string.your_week),
                action = stringResource(R.string.your_week_open),
                onAction = onOpenWeekly,
            )
        }
        gutterItem {
            WeekTicker(
                bosses = state.raidBossesThisWeek,
                keys = state.mythicRunsThisWeek,
                bestKey = state.bestKeyThisWeek,
                vaultDone = state.vaultQuestsDone,
                vaultTotal = state.vaultQuestsTotal,
                resetAt = state.weeklyResetAt,
                stale = state.weekStale || state.weekTrust == WeekTrust.STALE,
                trust = state.weekTrust,
                onClick = onOpenWeekly,
            )
        }
        gutterItem {
            Text(
                if (state.weekStale) {
                    stringResource(R.string.week_stale)
                } else {
                    stringResource(R.string.week_note)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.topPending.isNotEmpty()) {
            gutterItem {
                SectionHeader(stringResource(R.string.dashboard_pending))
            }
            items(state.topPending.size) { index ->
                PendingTaskRow(state.topPending[index], localeKey, onOpenWeekly)
            }
        }

        if (state.seasonMounts.isNotEmpty()) {
            gutterItem {
                SectionHeader(
                    stringResource(R.string.season_exclusive),
                    action = stringResource(R.string.season_see_all),
                    onAction = onOpenSeasonLoot,
                )
            }
            val pending = state.seasonMounts.sortedBy { it.owned }
            items(minOf(3, pending.size)) { index ->
                LootRow(pending[index], onClick = { onOpenSeasonLoot() })
            }
        }
    }
}

@Composable
private fun NextEventOverlay(
    name: String,
    zone: String,
    startsAt: Instant?,
    onPrepare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.let { base ->
            if (onPrepare != null) base.clickable(onClick = onPrepare) else base
        },
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            stringResource(R.string.next_event).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (startsAt != null) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = TextHigh,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
            if (zone.isNotBlank()) {
                Text(
                    zone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
            CountdownText(startsAt, style = MaterialTheme.typography.headlineSmall)
            if (onPrepare != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.prepare),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Text(
                stringResource(R.string.dashboard_no_events),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun WeekTicker(
    bosses: Int,
    keys: Int,
    bestKey: Int,
    vaultDone: Int,
    vaultTotal: Int,
    resetAt: Instant?,
    stale: Boolean,
    trust: WeekTrust,
    onClick: () -> Unit,
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(resetAt) {
        while (true) {
            now = Instant.now()
            delay(1000)
        }
    }
    val resetText = resetAt?.let { target ->
        val remaining = Duration.between(now, target)
        if (remaining.isNegative) "—" else formatDuration(remaining)
    } ?: "—"
    val bossesLabel = ThisWeek.countLabel(bosses, stale)
    val keysText = when {
        stale -> ThisWeek.countLabel(keys, true)
        bestKey > 0 -> stringResource(R.string.week_ticker_keys_best, keys, bestKey)
        else -> stringResource(R.string.week_ticker_keys, keys)
    }
    val vaultLabel = if (stale) "—" else "$vaultDone/$vaultTotal"
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        WeekTrustBadge(trust)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.week_ticker, bossesLabel, keysText, vaultLabel, resetText),
            style = MaterialTheme.typography.titleMedium,
            color = TextHigh,
        )
    }
}

@Composable
private fun PendingTaskRow(task: TrackedTask, localeKey: String, onOpenWeekly: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter)) {
        ListRow(
            title = task.title[localeKey] ?: task.title.values.firstOrNull().orEmpty(),
            subtitle = task.zone,
            onClick = onOpenWeekly,
        )
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
