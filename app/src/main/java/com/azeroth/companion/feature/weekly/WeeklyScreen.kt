package com.azeroth.companion.feature.weekly

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.azeroth.companion.R
import com.azeroth.companion.data.WeeklyActivity

/**
 * Actividad de la semana en curso, sin nada que marcar a mano.
 *
 * Las mazmorras M+ y los jefes de banda llevan fecha en la API: son exactos
 * desde el primer sync. Las Delves salen por diferencia de la estadística
 * acumulada. Y las semanales con nombre salen de las misiones que la app ha
 * aprendido que son repetibles observando tus propias sincronizaciones, que es
 * la única forma fiable: los IDs de las misiones marcador de Blizzard no
 * aparecen en el perfil.
 */
@Composable
fun WeeklyScreen(
    onOpenSource: (Int, Int) -> Unit = { _, _ -> },
    onOpenQuest: (Int) -> Unit = {},
    viewModel: WeeklyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val a = state.activity
    // stringResource solo puede llamarse en contexto @Composable, y el cuerpo de
    // LazyColumn no lo es: los títulos de sección se resuelven aquí.
    val titleWeeklies = stringResource(R.string.weekly_section_weeklies)
    val titleMplus = stringResource(R.string.weekly_section_mplus)
    val titleRaids = stringResource(R.string.weekly_section_raids)
    val titleDelves = stringResource(R.string.weekly_section_delves)
    val titleDetected = stringResource(R.string.weekly_section_detected)
    val titleOtherQuests = stringResource(R.string.weekly_section_other_quests)
    val titleQuestsOf = stringResource(R.string.weekly_quests_of)
    // El catálogo trae los títulos en ambos idiomas; se elige según el idioma
    // activo, que puede ser el del sistema o el que el usuario haya forzado.
    val localeKey = if (java.util.Locale.getDefault().language.startsWith("es")) "es_MX" else "en_US"

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.weekly_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!a.hasCharacter) {
            item { Empty(stringResource(R.string.weekly_need_login)) }
            return@LazyColumn
        }

        item { Summary(a) }

        // Lo primero de la pantalla: qué misiones quedan por hacer esta semana.
        // El resumen de actividad es contexto; esto es la acción.
        vaultQuestsSection(
            snapshot = state.vaultQuests,
            expanded = state.expandedGroups,
            onToggleGroup = viewModel::toggleGroup,
            onOpenQuest = onOpenQuest,
        )

        // Resumen por actividad. Cada fila se marca con una señal que la API
        // demuestra (mazmorras y jefes van fechados), no con IDs de misión
        // adivinados: los marcadores "Midnight: X" resultaron no existir en
        // /quests/completed, comprobado sobre 75 personajes activos.
        val done = state.tasks.count { (it.state?.completions ?: 0) > 0 }
        section(titleWeeklies, done) {
            if (state.tasks.isEmpty()) {
                Empty(stringResource(R.string.weekly_none_catalog))
            } else {
                Column {
                    state.tasks.forEach { row ->
                        val complete = (row.state?.completions ?: 0) > 0
                        val id = row.task.id
                        WeeklyTaskRow(
                            title = row.task.title[localeKey] ?: row.task.title.values.first(),
                            complete = complete,
                            expanded = state.expandedTaskId == id,
                            loot = state.lootByTask[id],
                            quests = state.questsByTask[id],
                            onToggle = { viewModel.toggleTask(id) },
                            onOpenSource = onOpenSource,
                            onOpenQuest = onOpenQuest,
                        )
                    }
                    Text(
                        stringResource(R.string.weekly_done_count, done, state.tasks.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        section(titleMplus, a.mythicRuns.size) {
            if (a.mythicRuns.isEmpty()) {
                Empty(stringResource(R.string.weekly_none_mplus))
            } else {
                a.mythicRuns.forEach { run ->
                    LineRow(
                        icon = if (run.inTime) "⏱" else "✔",
                        title = run.name.ifBlank { "Mazmorra" },
                        trailing = "+${run.level}",
                    )
                }
            }
        }

        section(titleRaids, a.raidKills.size) {
            if (a.raidKills.isEmpty()) {
                Empty(stringResource(R.string.weekly_none_raid))
            } else {
                a.raidKills.forEach { kill ->
                    LineRow("☠", kill.name.ifBlank { "Jefe" }, difficultyLabel(kill.difficulty))
                }
            }
        }

        section(titleDelves, a.delves ?: 0) {
            when {
                a.delves == null -> Empty(
                    stringResource(R.string.weekly_delves_pending),
                )
                a.delves == 0 -> Empty(stringResource(R.string.weekly_none_delves))
                else -> LineRow("🕳", stringResource(R.string.weekly_delves_done), a.delves.toString())
            }
        }

        // Las semanales que el jugador HA HECHO, por nombre. Salen de las
        // misiones repetibles que la app ha aprendido de su propia cuenta: no
        // dependen de una lista de IDs escrita a mano.
        // Red de seguridad: aunque el catálogo no cubra una semanal concreta, la
        // app aprende qué misiones son repetibles observando tus propios syncs y
        // las muestra aquí POR NOMBRE, así que nunca te quedas sin ver lo hecho.
        section(titleDetected, a.repeatableDone.size) {
            when {
                a.learnedRepeatables == 0 -> Empty(
                    stringResource(R.string.weekly_learning),
                )
                a.repeatableDone.isEmpty() -> Empty(
                    stringResource(R.string.weekly_none_repeatable, a.learnedRepeatables),
                )
                else -> a.repeatableDone.forEach { q -> LineRow("☑", q.name, stringResource(R.string.weekly_done)) }
            }
        }

        section(titleOtherQuests, a.quests.size) {
            when {
                !a.hasBaseline -> Empty(
                    stringResource(R.string.weekly_quests_pending),
                )
                a.quests.isEmpty() -> Empty(stringResource(R.string.weekly_none_quests))
                else -> a.quests.forEach { q -> LineRow("✓", q.name, "") }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    count: Int,
    content: @Composable () -> Unit,
) {
    item(key = "sec_$title") {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            content()
        }
    }
}

/**
 * Una semanal con el botín del contenido que pide: la misión de seguimiento solo
 * da oro, así que lo que responde "¿por qué hago esto?" es lo que cae dentro.
 */
@Composable
private fun WeeklyTaskRow(
    title: String,
    complete: Boolean,
    expanded: Boolean,
    loot: List<com.azeroth.companion.data.LootEntry>?,
    quests: List<com.azeroth.companion.data.WeeklyQuestDone>?,
    onToggle: () -> Unit,
    onOpenSource: (Int, Int) -> Unit,
    onOpenQuest: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 5.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (complete) "☑" else "☐", style = MaterialTheme.typography.bodyMedium)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (complete) {
                Text(
                    stringResource(R.string.weekly_done),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                if (expanded) "▾" else "▸",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 26.dp, top = 4.dp)) {
                // Las misiones concretas de la semanal: tocar una abre su ficha
                // con botín, zona y el comando de TomTom.
                if (!quests.isNullOrEmpty()) {
                    Text(
                        stringResource(R.string.weekly_quests_of),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    quests.forEach { quest ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onOpenQuest(quest.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(if (quest.completed) "☑" else "☐",
                                style = MaterialTheme.typography.bodySmall)
                            Text(quest.name, Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            Text("›", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                when {
                    // Solo hay botín asociado en las semanales que apuntan a una
                    // instancia; en las demás basta con la lista de misiones.
                    !loot.isNullOrEmpty() -> {
                        Text(
                            stringResource(R.string.weekly_loot_intro),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        loot.forEach { entry ->
                            com.azeroth.companion.ui.components.LootRow(
                                entry,
                                onClick = { onOpenSource(it.instanceId, it.bossId) },
                            )
                        }
                    }
                    quests == null && loot == null -> Empty(stringResource(R.string.weekly_loot_loading))
                    quests.isNullOrEmpty() -> Empty(stringResource(R.string.weekly_loot_none))
                }
            }
        }
    }
}

@Composable
private fun Summary(a: WeeklyActivity) {
    Panel(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Stat(a.mythicRuns.size.toString(), stringResource(R.string.stat_mplus))
            Stat(a.raidKills.size.toString(), stringResource(R.string.stat_bosses))
            Stat(a.delves?.toString() ?: "—", stringResource(R.string.stat_delves))
            Stat(if (a.hasBaseline) a.quests.size.toString() else "—", stringResource(R.string.stat_quests))
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LineRow(icon: String, title: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(icon, style = MaterialTheme.typography.bodyMedium)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (trailing.isNotBlank()) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

private fun difficultyLabel(type: String): String = when (type.uppercase()) {
    "LFR" -> "Buscador"
    "NORMAL" -> "Normal"
    "HEROIC" -> "Heroico"
    "MYTHIC" -> "Mítico"
    else -> type
}
