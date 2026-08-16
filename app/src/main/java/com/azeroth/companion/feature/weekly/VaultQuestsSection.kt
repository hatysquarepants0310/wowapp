package com.azeroth.companion.feature.weekly

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azeroth.companion.R
import com.azeroth.companion.data.VaultQuest
import com.azeroth.companion.data.VaultQuestGroup
import com.azeroth.companion.data.VaultQuestsSnapshot
import com.azeroth.companion.ui.components.Divider
import com.azeroth.companion.ui.components.Panel
import com.azeroth.companion.ui.components.Pill
import com.azeroth.companion.ui.components.ProgressTrack
import com.azeroth.companion.ui.components.Radius
import com.azeroth.companion.ui.components.SectionHeader
import com.azeroth.companion.ui.components.Spacing
import com.azeroth.companion.ui.components.WeekTrustBadge
import com.azeroth.companion.ui.theme.Gold
import com.azeroth.companion.ui.theme.Positive
import com.azeroth.companion.ui.theme.Warning

/**
 * Las misiones de la semana, una por una.
 *
 * La versión anterior enseñaba diecisiete tareas abstractas con un contador
 * "3/99". Servía para comprobar si algo estaba hecho, pero no para lo que la
 * gente viene a buscar: qué me queda por hacer y dónde. Aquí cada fila es una
 * misión con nombre y zona, se tacha al completarla y al pulsarla se abre su
 * ficha con el botín y el comando de TomTom.
 */
fun LazyListScope.vaultQuestsSection(
    snapshot: VaultQuestsSnapshot,
    expanded: Set<String>,
    onToggleGroup: (String) -> Unit,
    onOpenQuest: (Int) -> Unit,
) {
    if (snapshot.groups.isEmpty()) return

    item {
        SectionHeader(stringResource(R.string.vault_quests_title))
    }

    if (snapshot.vaultTotal > 0) {
        item {
            Panel(padding = PaddingValues(Spacing.lg)) {
                WeekTrustBadge(snapshot.weekTrust)
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.vault_quests_progress).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        // La frase, no la fracción.
                        //
                        // "3 / 19" obliga a restar para saber lo único que
                        // importa. Lo que se viene a preguntar es "¿cuánto me
                        // falta?", así que se responde con eso y la fracción
                        // queda debajo, en pequeño, para quien la quiera.
                        Text(
                            if (snapshot.vaultPending == 0) {
                                stringResource(R.string.vault_quests_all_done)
                            } else {
                                stringResource(
                                    R.string.vault_quests_pending,
                                    snapshot.vaultPending,
                                )
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (snapshot.vaultPending == 0) Positive else Gold,
                        )
                        Text(
                            "${snapshot.vaultDone} / ${snapshot.vaultTotal}",
                            style = com.azeroth.companion.ui.theme.NumberStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                ProgressTrack(
                    snapshot.vaultDone.toFloat() / snapshot.vaultTotal,
                    color = Gold,
                )
                Spacer(Modifier.height(Spacing.md))
                if (snapshot.staleForThisWeek) {
                    // Sin este aviso la lista miente con toda la confianza del
                    // mundo: enseñaría como hechas cosas de la semana pasada
                    // que ya han vuelto a estar disponibles.
                    Text(
                        stringResource(R.string.vault_quests_stale),
                        style = MaterialTheme.typography.bodySmall,
                        color = Warning,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
                Text(
                    stringResource(R.string.vault_quests_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (snapshot.hiddenForever > 0) {
                    // Se ocultaron cosas: hay que decirlo. Ocultar en silencio
                    // hace que el usuario dude de si la app las conoce.
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(
                            R.string.vault_quests_hidden_forever,
                            snapshot.hiddenForever,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    snapshot.groups.forEach { group ->
        item(key = "group-${group.taskId}") {
            GroupHeader(group, expanded = group.taskId in expanded) { onToggleGroup(group.taskId) }
        }
        if (group.taskId in expanded) {
            items(group.quests.size, key = { "${group.taskId}-${group.quests[it].questId}" }) { index ->
                val quest = group.quests[index]
                Column {
                    QuestRow(quest, onOpenQuest)
                    if (index < group.quests.lastIndex) Divider()
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: VaultQuestGroup, expanded: Boolean, onClick: () -> Unit) {
    val complete = group.doneCount == group.quests.size
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.none))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(group.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${group.doneCount}/${group.quests.size}",
                style = MaterialTheme.typography.bodySmall,
                color = if (complete) Positive else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (group.feedsVault) {
            Pill(stringResource(R.string.vault_quests_feeds), color = Gold)
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            if (expanded) "−" else "+",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuestRow(quest: VaultQuest, onOpenQuest: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.none))
            .clickable { onOpenQuest(quest.questId) }
            .padding(vertical = Spacing.md, horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (quest.done) Positive.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (quest.done) {
                Text("✓", style = MaterialTheme.typography.labelMedium, color = Positive)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                quest.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (quest.done) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (quest.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            quest.zone?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
