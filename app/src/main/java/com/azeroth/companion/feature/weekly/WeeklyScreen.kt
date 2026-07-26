package com.azeroth.companion.feature.weekly

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
import androidx.compose.material3.Card
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
import com.azeroth.companion.data.WeeklyActivity

/**
 * Actividad de la semana en curso, sin nada que marcar a mano: todo sale de la
 * cuenta de Battle.net. Las mazmorras y los jefes llevan fecha en la API, así
 * que son exactos desde el primer sync; las Delves y las misiones se deducen
 * comparando con el último snapshot anterior al reset.
 */
@Composable
fun WeeklyScreen(
    onOpenSource: (Int, Int) -> Unit = { _, _ -> },
    viewModel: WeeklyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val a = state.activity

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Todo lo que has hecho desde el reset semanal, leído de tu cuenta " +
                    "de Battle.net. Nada se marca a mano.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!a.hasCharacter) {
            item { Empty("Inicia sesión y sincroniza para ver tu semana.") }
            return@LazyColumn
        }

        item { Summary(a) }

        // Las semanales de la expansión: cada fila es una misión real de Blizzard
        // ("Midnight: <actividad>"), así que se marca sola al sincronizar.
        val done = state.tasks.count { (it.state?.completions ?: 0) > 0 }
        section("Semanales", done) {
            if (state.tasks.isEmpty()) {
                Empty("Sin semanales en el catálogo para esta expansión.")
            } else {
                Column {
                    state.tasks.forEach { row ->
                        val complete = (row.state?.completions ?: 0) > 0
                        val id = row.task.id
                        WeeklyTaskRow(
                            title = row.task.title["es_MX"] ?: row.task.title.values.first(),
                            complete = complete,
                            expandable = row.task.lootInstanceIds.isNotEmpty(),
                            expanded = state.expandedTaskId == id,
                            loot = state.lootByTask[id],
                            onToggle = { viewModel.toggleTask(id) },
                            onOpenSource = onOpenSource,
                        )
                    }
                    Text(
                        "$done de ${state.tasks.size} hechas esta semana.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        section("Mythic+", a.mythicRuns.size) {
            if (a.mythicRuns.isEmpty()) {
                Empty("Ninguna mazmorra Mythic+ esta semana.")
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

        section("Bandas", a.raidKills.size) {
            if (a.raidKills.isEmpty()) {
                Empty("Ningún jefe de banda esta semana.")
            } else {
                a.raidKills.forEach { kill ->
                    LineRow("☠", kill.name.ifBlank { "Jefe" }, difficultyLabel(kill.difficulty))
                }
            }
        }

        section("Profundidades", a.delves ?: 0) {
            when {
                a.delves == null -> Empty(
                    "Se contarán a partir del próximo reset: la API solo da el total " +
                        "acumulado, así que la app necesita una lectura anterior al reset " +
                        "para saber cuáles son de esta semana.",
                )
                a.delves == 0 -> Empty("Ninguna Delve esta semana.")
                else -> LineRow("🕳", "Delves completadas", a.delves.toString())
            }
        }

        section("Misiones", a.quests.size) {
            when {
                !a.hasBaseline -> Empty(
                    "Se listarán a partir del próximo reset, cuando la app tenga una " +
                        "lectura previa con la que comparar.",
                )
                a.quests.isEmpty() -> Empty("Ninguna misión completada desde el reset.")
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
    expandable: Boolean,
    expanded: Boolean,
    loot: List<com.azeroth.companion.data.LootEntry>?,
    onToggle: () -> Unit,
    onOpenSource: (Int, Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .let { m -> if (expandable) m.clickable { onToggle() } else m }
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
                    "hecha",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expandable) {
                Text(
                    if (expanded) "▾" else "▸",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 26.dp, top = 4.dp)) {
                if (loot == null) {
                    Empty("Cargando botín…")
                } else if (loot.isEmpty()) {
                    Empty("Sin botín listado para este contenido.")
                } else {
                    Text(
                        "Lo que se persigue aquí:",
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
            }
        }
    }
}

@Composable
private fun Summary(a: WeeklyActivity) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Stat(a.mythicRuns.size.toString(), "M+")
            Stat(a.raidKills.size.toString(), "Jefes")
            Stat(a.delves?.toString() ?: "—", "Delves")
            Stat(if (a.hasBaseline) a.quests.size.toString() else "—", "Misiones")
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
