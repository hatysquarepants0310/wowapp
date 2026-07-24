package com.azeroth.companion.feature.progression

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.ui.components.SectionCard

/**
 * Progresión (§9.4): Folio Omnium · Presas · Campaña · Delves y monedas.
 * Todo el estado es editable a mano — la API no expone estos sistemas, así que
 * el usuario es la fuente de verdad y el estado persiste por personaje.
 */
@Composable
fun ProgressionScreen(viewModel: ProgressionViewModel = hiltViewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Folio", "Presas", "Campaña", "Delves")
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> FolioTab(state, viewModel)
                1 -> PreyTab(state, viewModel)
                2 -> CampaignTab()
                3 -> DelvesTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onChange(value - 1) }, enabled = value > 0) { Text("−") }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange(value + 1) }, enabled = value < max) { Text("+") }
    }
}

@Composable
private fun FolioTab(state: ProgressionUiState, viewModel: ProgressionViewModel) {
    SectionCard("Folio Omnium — ${state.folioRows}/${state.folioTotalRows} filas") {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { state.folioRows / state.folioTotalRows.toFloat() })
        Spacer(Modifier.height(12.dp))
        Text(
            "La API de Blizzard no expone el Folio: ajusta aquí solo si el dato difiere de tu juego.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Stepper("Filas desbloqueadas", state.folioRows, state.folioTotalRows, viewModel::setFolioRows)
        Stepper("Semanas de catch-up pendientes", state.folioCatchUp, 5, viewModel::setFolioCatchUp)
        if (state.folioCatchUp > 0) {
            Text(
                "Catch-up disponible: haz las ${state.folioCatchUp} semanas pendientes de forma " +
                    "secuencial, una a la vez, sin esperar el reset. No existe moneda para saltar pasos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
    SectionCard("Reglas del sistema") {
        Text(
            "· 1 fila por reset semanal; cada fila ofrece 2–4 runas y eliges una (recambiable fuera de combate).\n" +
                "· La questline inicia en Ciudad Solaz (\"La Llamada del Magister\") y sigue en Quel'Danas.\n" +
                "· ⚠ Consulta la runa recomendada por clase ANTES de elegir la Runa Central: condiciona la build.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PreyTab(state: ProgressionUiState, viewModel: ProgressionViewModel) {
    val zones = listOf(
        "eversong" to "Bosques de Canción Eterna",
        "zulaman" to "Zul'Aman",
        "harandar" to "Harandar",
        "voidstorm" to "Voidstorm",
    )
    zones.forEach { (id, label) ->
        val progress = state.prey[id] ?: 0
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text("$progress%", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = progress.toFloat(),
                    onValueChange = { viewModel.setPrey(id, it.toInt()) },
                    valueRange = 0f..100f,
                )
                if (progress >= 100) {
                    Text("¡Presa revelada! Habla con Astalor para invocarla y reclamar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
    Text(
        "Límite: una cacería por dificultad, por semana, por zona. La barra sube con contenido " +
            "de mundo en la zona; si la presa te embosca, seguir el rastro de niebla de sangre y " +
            "atacarla también aumenta el progreso.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CampaignTab() {
    SectionCard("1.º — La guerra de la Luz y la Sombra") {
        Text(
            "6 capítulos. Hacer PRIMERO: desbloquea los sistemas de endgame.\n" +
                "Recompensas: set Atavíos del Pacto de Solestrella · montura Dracohalcón Peridoto.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    SectionCard("2.º — La maldición de Ula'tek") {
        Text("Contenido 12.1, Isla Enroscada. Hacer después de la campaña base.",
            style = MaterialTheme.typography.bodyMedium)
    }
    Text("La campaña es progresión única: no se resetea. Márcala en la checklist Semanal (Historia).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DelvesTab(state: ProgressionUiState, viewModel: ProgressionViewModel) {
    SectionCard("Profundidades") {
        Spacer(Modifier.height(8.dp))
        Stepper("Restored Coffer Keys disponibles", state.delveKeys, 20, viewModel::setDelveKeys)
        Stepper("Bountiful Delves hechas esta semana", state.delvesDone, 20, viewModel::setDelvesDone)
    }
    SectionCard("Monedas y calculadora de mejora") {
        Spacer(Modifier.height(8.dp))
        Stepper("Dawncrests totales", state.crestsTotal, 999, viewModel::setCrestsTotal)
        Stepper(
            "Ganados esta semana (tope ${state.economy.weeklyCrestCap})",
            state.crestsThisWeek,
            state.economy.weeklyCrestCap,
            viewModel::setCrestsThisWeek,
        )
        Spacer(Modifier.height(8.dp))
        val plan = state.upgradePlan
        Text(
            "Con ${state.crestsTotal} crests puedes pagar ${plan.stepsAffordable} mejora(s) " +
                "(+${plan.ilvlGain} ilvl en total, a ${state.economy.crestCostPerUpgrade} crests y " +
                "+${state.economy.ilvlPerUpgrade} ilvl por paso). Te sobran ${plan.crestsLeft}; " +
                "faltan ${plan.crestsToNextStep} para el siguiente paso.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (state.crestsThisWeek >= state.economy.weeklyCrestCap) {
            Text("Tope semanal de crests alcanzado. 🎉", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary)
        }
    }
}
