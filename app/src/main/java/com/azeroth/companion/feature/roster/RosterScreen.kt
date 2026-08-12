package com.azeroth.companion.feature.roster

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.database.CharacterDao
import com.azeroth.companion.core.database.TaskStateDao
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.data.WeekSummary
import com.azeroth.companion.data.EventsRepository
import com.azeroth.companion.data.ProgressionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class RosterRow(
    val id: Long,
    val name: String,
    val realmName: String,
    val playableClass: String,
    val activeSpec: String?,
    val ilvl: Int,
    val isActive: Boolean,
    val week: WeekSummary,
    val avatarUrl: String?,
    /** Intentos de montura del Stormarion restantes esta semana (límite por personaje). */
    val mountAttemptsLeft: Int,
)

data class RosterState(val loading: Boolean = true, val rows: List<RosterRow> = emptyList())

@HiltViewModel
class RosterViewModel @Inject constructor(
    private val characterDao: CharacterDao,
    private val taskStateDao: TaskStateDao,
    private val settingsRepository: SettingsRepository,
    private val progressionRepository: ProgressionRepository,
    private val eventsRepository: EventsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RosterState())
    val state: StateFlow<RosterState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                val settings = settingsRepository.settings.first()
                val lastReset = eventsRepository.resetClock().lastWeeklyReset(Instant.now())
                val rows = characterDao.observeAll().first().map { c ->
                    val week = runCatching { progressionRepository.weekSummary(c.id) }
                        .getOrDefault(WeekSummary())
                    val stormarion = taskStateDao.get(c.id, "weekly_stormarion_assault")
                        ?.takeIf { !it.periodStart.isBefore(lastReset) }
                    RosterRow(
                        id = c.id,
                        name = c.name,
                        realmName = c.realmName,
                        playableClass = c.playableClass,
                        activeSpec = c.activeSpec,
                        ilvl = c.equippedItemLevel,
                        isActive = c.id == settings.activeCharacterId,
                        week = week,
                        avatarUrl = c.avatarUrl,
                        mountAttemptsLeft = (2 - (stormarion?.completions ?: 0)).coerceAtLeast(0),
                    )
                }
                _state.value = RosterState(loading = false, rows = rows)
            }.onFailure { _state.value = RosterState(loading = false) }
        }
    }

    fun setActive(characterId: Long) {
        viewModelScope.launch {
            settingsRepository.setActiveCharacter(characterId)
            refresh()
        }
    }
}

@Composable
fun RosterScreen(viewModel: RosterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (!state.loading && state.rows.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Sin personajes sincronizados", style = MaterialTheme.typography.titleMedium)
            Text(
                "Inicia sesión con Battle.net en Ajustes para importar tu roster. " +
                    "Los intentos de montura y los límites semanales son por personaje.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.rows, key = { it.id }) { row ->
            Card(
                Modifier.fillMaxWidth().clickable { viewModel.setActive(row.id) },
                colors = if (row.isActive) {
                    androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                } else {
                    androidx.compose.material3.CardDefaults.cardColors()
                },
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.Top,
                ) {
                    // La cara del personaje: un roster de texto plano no dice
                    // nada, y Blizzard publica el avatar de cada uno.
                    com.azeroth.companion.ui.components.CharacterAvatar(
                        avatarUrl = row.avatarUrl,
                        className = row.playableClass,
                        size = 44.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${row.name} · ${row.realmName}",
                            style = MaterialTheme.typography.titleSmall,
                            color = com.azeroth.companion.ui.theme.ClassColors
                                .forClassName(row.playableClass),
                        )
                        if (row.isActive) {
                            Text("ACTIVO", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        "${row.playableClass}${row.activeSpec?.let { " · $it" } ?: ""} · ilvl ${row.ilvl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Actividad de la semana con datos fechados por Blizzard.
                    // Antes aquí iba una previsión de Gran Bóveda que la API no
                    // permite sostener.
                    Text(
                        "Esta semana — ${row.week.raidBosses} jefes · " +
                            "${row.week.mythicRuns} llaves · ${row.week.delves} abismos",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Intentos de montura restantes esta semana: ${row.mountAttemptsLeft}/2",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (row.mountAttemptsLeft > 0) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                }
            }
        }
        item {
            Text(
                "Toca un personaje para hacerlo el activo (sync, checklist y Bóveda del inicio).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
