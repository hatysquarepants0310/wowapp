package com.azeroth.companion.feature.score

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.ui.components.WowLoading
import com.azeroth.companion.R
import com.azeroth.companion.data.MythicRun
import com.azeroth.companion.ui.components.Divider
import com.azeroth.companion.ui.components.EmptyState
import com.azeroth.companion.ui.components.ListRow
import com.azeroth.companion.ui.components.Panel
import com.azeroth.companion.ui.components.Pill
import com.azeroth.companion.ui.components.Screen
import com.azeroth.companion.ui.components.ScreenTitle
import com.azeroth.companion.ui.components.SectionHeader
import com.azeroth.companion.ui.components.Spacing
import com.azeroth.companion.ui.theme.Positive

/**
 * Puntuación de mítica+, llaves y progreso de banda.
 *
 * Blizzard publica las llaves pero no la puntuación: el número que la comunidad
 * usa lo calcula Raider.IO. Aquí se muestra tal cual, con su color de tramo, y
 * se dice de dónde viene.
 */
@Composable
fun ScoreScreen(viewModel: ScoreViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Screen {
        item {
            ScreenTitle(
                stringResource(R.string.title_score),
                subtitle = state.characterName?.let {
                    stringResource(R.string.score_subtitle, it)
                },
            )
        }

        when {
            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(Spacing.xxl), Alignment.Center) {
                    WowLoading()
                }
            }

            state.error != null -> item {
                EmptyState(
                    title = stringResource(R.string.score_unavailable),
                    detail = state.error,
                )
            }

            state.profile == null -> item {
                EmptyState(title = stringResource(R.string.score_unavailable))
            }

            else -> {
                val profile = state.profile!!

                item {
                    Panel(padding = PaddingValues(Spacing.xl)) {
                        Text(
                            stringResource(R.string.score_mplus).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "%.1f".format(profile.score),
                            style = MaterialTheme.typography.displayMedium,
                            // El color del tramo lo publica Raider.IO; es el mismo
                            // que ve el jugador en su web y en los addons.
                            color = parseColor(profile.scoreColor)
                                ?: MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            stringResource(R.string.score_source),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (profile.weeklyRuns.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.score_this_week)) }
                    items(profile.weeklyRuns.size) { index ->
                        val run = profile.weeklyRuns[index]
                        Column {
                            RunRow(run, highlight = true)
                            if (index < profile.weeklyRuns.lastIndex) Divider()
                        }
                    }
                }

                if (profile.bestRuns.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.score_best_runs)) }
                    items(profile.bestRuns.size) { index ->
                        val run = profile.bestRuns[index]
                        Column {
                            RunRow(run, highlight = false)
                            if (index < profile.bestRuns.lastIndex) Divider()
                        }
                    }
                }

                val raids = profile.raids.filter { it.totalBosses > 1 }
                if (raids.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.score_raids)) }
                    items(raids.size) { index ->
                        val raid = raids[index]
                        Column {
                            ListRow(
                                title = raid.name,
                                subtitle = stringResource(
                                    R.string.score_raid_detail,
                                    raid.normal, raid.heroic, raid.mythic,
                                ),
                                trailing = raid.summary,
                            )
                            if (index < raids.lastIndex) Divider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunRow(run: MythicRun, highlight: Boolean) {
    ListRow(
        title = run.dungeon,
        subtitle = run.clearTimeMs?.let { formatTime(it) },
        trailing = run.score?.let { "%.0f".format(it) },
        leading = {
            Pill(
                // Las mejoras de llave son las "estrellas" que todo el mundo
                // mira: +2 significa dos niveles ganados.
                buildString {
                    append('+').append(run.level)
                    if (run.upgrades > 0) append(" ").append("★".repeat(run.upgrades))
                },
                color = if (highlight) Positive else MaterialTheme.colorScheme.primary,
            )
        },
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** `#ff8000` → Color. Devuelve null si Raider.IO manda algo que no entendemos. */
private fun parseColor(hex: String?): Color? {
    val clean = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    return clean.toLongOrNull(16)?.let { Color(it or 0xFF000000L) }
}
