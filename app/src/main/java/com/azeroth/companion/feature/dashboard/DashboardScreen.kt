package com.azeroth.companion.feature.dashboard

import androidx.compose.foundation.lazy.LazyListScope
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
import com.azeroth.companion.ui.components.ConfidenceBadge
import com.azeroth.companion.ui.components.ProgressTrack
import com.azeroth.companion.ui.components.CountdownText
import com.azeroth.companion.ui.components.Divider
import com.azeroth.companion.ui.components.CharacterHero
import com.azeroth.companion.ui.components.DataRow
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
/**
 * Un bloque con el margen lateral de la app.
 *
 * Existe para que el banner del personaje pueda NO tenerlo. Un banner que llega
 * al borde se lee como una imagen; con 16dp de negro alrededor se lee como una
 * tarjeta con una foto dentro, que es justo lo contrario de lo que se busca.
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

    // Sin margen lateral en el contenedor: lo aplica cada bloque con
    // `gutterItem`. Es lo que permite que el banner del personaje llegue hasta
    // el borde de la pantalla en vez de quedarse con 16dp de negro a los lados,
    // que es lo que separa un banner de una tarjeta más.
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

        // ---- Tu personaje preside -----------------------------------------
        //
        // Blizzard publica un render de cuerpo entero de cada personaje. Es lo
        // que hace que esto no sea una plantilla: en la pantalla está TU gnomo
        // con su equipo puesto, y el acento de toda la app sale del color de su
        // clase. Por eso el resto de la interfaz puede estar callada.
        state.activeCharacterName?.let { name ->
            item {
                CharacterHero(
                    name = name,
                    realm = state.activeCharacterRealm,
                    className = state.activeCharacterClass,
                    spec = state.activeCharacterSpec,
                    itemLevel = state.activeCharacterIlvl,
                    renderUrl = state.activeCharacterRender,
                    onClick = onOpenRoster,
                    trailing = { SyncFreshness(state.lastSyncedAt) },
                )
            }
        }

        // ---- Lo que caduca: el próximo evento y el reset -------------------
        gutterItem {
            Panel(padding = PaddingValues(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.next_event).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.nextEventStartsAt != null) {
                            Text(
                                state.nextEventName,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                state.nextEventZone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                stringResource(R.string.dashboard_no_events),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    if (state.nextEventStartsAt != null) {
                        Spacer(Modifier.width(Spacing.md))
                        Column(horizontalAlignment = Alignment.End) {
                            CountdownText(
                                state.nextEventStartsAt!!,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Pill(
                                stringResource(R.string.prepare),
                                color = MaterialTheme.colorScheme.primary,
                                filled = true,
                                modifier = Modifier.clickable {
                                    state.nextEventId?.let(onOpenChecklist)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Divider()
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.weekly_reset),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    state.weeklyResetAt?.let {
                        CountdownText(it, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }


        // ---- Tu semana ---------------------------------------------------
        //
        // Antes aquí había una rejilla 3x3 de Gran Bóveda con el ilvl que iba a
        // tocar en cada casilla. Era mentira: ni la API de Blizzard ni Raider.IO
        // exponen la Bóveda, así que aquello se deducía comparando lecturas
        // guardadas y acertaba de casualidad. Ahora se enseña solo lo que la
        // API afirma con fecha propia, y para saber qué falta por hacer está la
        // lista de misiones de la semana.
        gutterItem {
            SectionHeader(
                stringResource(R.string.your_week),
                action = stringResource(R.string.your_week_open),
                onAction = onOpenWeekly,
            )
        }
        gutterItem {
            Panel(padding = PaddingValues(Spacing.lg)) {
                DataRow(
                    stringResource(R.string.week_raid_bosses),
                    state.raidBossesThisWeek.toString(),
                )
                DataRow(
                    stringResource(R.string.week_mplus),
                    state.mythicRunsThisWeek.toString(),
                    hint = state.bestKeyThisWeek.takeIf { it > 0 }
                        ?.let { stringResource(R.string.week_best_key, it) },
                )
                DataRow(
                    stringResource(R.string.week_vault_quests),
                    "${state.vaultQuestsDone}/${state.vaultQuestsTotal}",
                    valueColor = Gold,
                )
                if (state.vaultQuestsTotal > 0) {
                    Spacer(Modifier.height(Spacing.lg))
                    ProgressTrack(
                        state.vaultQuestsDone.toFloat() / state.vaultQuestsTotal,
                        color = Gold,
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    // Un 0 porque Blizzard todavía no publica la semana NO es
                    // lo mismo que un 0 porque no has hecho nada, y confundirlo
                    // era justo lo que hacía parecer roto el contador.
                    if (state.weekStale) {
                        stringResource(R.string.week_stale)
                    } else {
                        stringResource(R.string.week_note)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                // Sí se puede saber qué monturas tienes: la colección del
                // personaje viene en /collections/mounts, así que el check y el
                // porcentaje son datos reales, no una estimación.
                val owned = state.seasonMounts.count { it.owned }
                val total = state.seasonMounts.size
                Panel(padding = PaddingValues(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.season_owned, owned, total),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${if (total == 0) 0 else owned * 100 / total}%",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Gold,
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    ProgressTrack(
                        if (total == 0) 0f else owned.toFloat() / total,
                        color = Gold,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        stringResource(R.string.season_exclusive_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Primero lo que falta: lo ya conseguido no es una tarea pendiente.
            val pending = state.seasonMounts.sortedBy { it.owned }
            items(minOf(3, pending.size)) { index ->
                LootRow(pending[index], onClick = { onOpenSeasonLoot() })
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

/** Un dato de la semana: cifra grande y etiqueta pequeña encima. */
