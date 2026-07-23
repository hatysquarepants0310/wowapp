package com.azeroth.companion.feature.progression

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/**
 * Progresión (§9.4): Folio Omnium · Presas · Campaña · Delves.
 * El estado detallado por personaje se llena con la sesión de Battle.net
 * (Fase 3); sin sesión, muestra las reglas del sistema para planificar.
 */
@Composable
fun ProgressionScreen() {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Folio Omnium", "Presas", "Campaña", "Delves")

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            when (tab) {
                0 -> InfoBlock(
                    "Árbol de 5 filas; se desbloquea 1 fila por reset semanal.",
                    "Cada fila ofrece 2–4 runas; eliges una y puedes recambiarla fuera de combate sin costo.",
                    "Catch-up: si vas atrasado, haces las semanas pendientes de forma secuencial, una a la vez, sin esperar el reset. No existe moneda para saltar pasos.",
                    "La questline inicia en Ciudad Solaz (\"La Llamada del Magister\") y sigue en la Isla de Quel'Danas. Cada paso otorga un Vestigio de Indagación Omnial.",
                    "⚠ Consulta la runa recomendada por clase ANTES de elegir la Runa Central: condiciona el resto de la build.",
                )
                1 -> InfoBlock(
                    "4 contratos simultáneos, uno por zona: Bosques de Canción Eterna, Zul'Aman, Harandar y Voidstorm.",
                    "La barra sube haciendo contenido de mundo en la zona: world quests, rares, tesoros, materiales de profesión, trampas y emboscadas.",
                    "Si la presa te embosca, sigue el rastro de niebla de sangre y atácala: aumenta el progreso.",
                    "Al completar, Astalor revela la ubicación: se invoca, se mata y se reclama.",
                    "Límite: una cacería por dificultad, por semana, por zona. Recompensas: cofre hasta track Champion, Dawncrests, fragmentos de Restored Coffer Key y progreso al slot de mundo de la Bóveda.",
                )
                2 -> InfoBlock(
                    "1.º — \"La guerra de la Luz y la Sombra\" (6 capítulos): desbloquea los sistemas de endgame. Recompensas: set Atavíos del Pacto de Solestrella y montura Dracohalcón Peridoto.",
                    "2.º — \"La maldición de Ula'tek\" (12.1, Isla Enroscada): hacer después de la campaña base.",
                    "La campaña es progresión única: no se resetea.",
                )
                3 -> InfoBlock(
                    "Las Bountiful Delves consumen Restored Coffer Keys y dan cofre con equipo.",
                    "Registra tus llaves disponibles y las Delves hechas esta semana en la checklist Semanal.",
                )
            }
        }
    }
}

@Composable
private fun InfoBlock(vararg lines: String) {
    lines.forEach {
        Text(it, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp))
    }
}
