package com.azeroth.companion.feature.progression

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.core.model.SlotProgress
import com.azeroth.companion.ui.components.ConfidenceBadge
import com.azeroth.companion.ui.components.SectionCard

/**
 * Progresión (§9.4), automática: Bóveda desde el sync; Folio, Presas y Delves
 * como guía del sistema (la API de Blizzard no los expone).
 */
@Composable
fun ProgressionScreen(viewModel: ProgressionViewModel = hiltViewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Folio", "Presas", "Delves")
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
                0 -> InfoBlock(
                    "Árbol de 5 filas; se desbloquea 1 fila por reset semanal.",
                    "Cada fila ofrece 2–4 runas; eliges una y puedes recambiarla fuera de combate sin costo.",
                    "Catch-up: si vas atrasado, haces las semanas pendientes de forma secuencial, una a la vez, sin esperar el reset. No existe moneda para saltar pasos.",
                    "La questline inicia en Ciudad Solaz (\"La Llamada del Magister\") y sigue en la Isla de Quel'Danas.",
                    "⚠ Consulta la runa recomendada por clase ANTES de elegir la Runa Central: condiciona el resto de la build.",
                )
                1 -> InfoBlock(
                    "4 contratos simultáneos, uno por zona: Bosques de Canción Eterna, Zul'Aman, Harandar y Voidstorm.",
                    "La barra sube haciendo contenido de mundo en la zona: world quests, rares, tesoros, materiales de profesión, trampas y emboscadas.",
                    "Si la presa te embosca, sigue el rastro de niebla de sangre y atácala: también aumenta el progreso.",
                    "Al completar, Astalor revela la ubicación: se invoca, se mata y se reclama.",
                    "Límite: una cacería por dificultad, por semana, por zona. Recompensas: cofre hasta track Champion, Dawncrests, fragmentos de Restored Coffer Key y progreso al slot de mundo de la Bóveda.",
                )
                2 -> DelvesTab(state)
            }
        }
    }
}

@Composable
private fun DelvesTab(state: ProgressionUiState) {
    InfoBlock(
        "Las Bountiful Delves consumen Restored Coffer Keys y dan cofre con equipo (hasta track Champion).",
        "Cuentan para el slot de mundo de la Gran Bóveda: se detectan automáticamente con el sync.",
    )
    SectionCard("Economía de mejora") {
        Spacer(Modifier.height(6.dp))
        Text(
            "Cada mejora cuesta ${state.economy.crestCostPerUpgrade} ${state.economy.crestName["es_MX"] ?: "crests"} " +
                "y sube +${state.economy.ilvlPerUpgrade} ilvl (máx. ${state.economy.maxUpgradeStepsPerItem} pasos por pieza). " +
                "Tope semanal: ${state.economy.weeklyCrestCap} crests.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun InfoBlock(vararg lines: String) {
    lines.forEach {
        Text(it, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp))
    }
}
