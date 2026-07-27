package com.azeroth.companion.feature.weekly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.model.TaskCategory
import com.azeroth.companion.data.ActiveCharacter
import com.azeroth.companion.data.EventsRepository
import com.azeroth.companion.data.TaskWithState
import com.azeroth.companion.data.WeeklyActivity
import com.azeroth.companion.data.WeeklyActivityRepository
import com.azeroth.companion.data.WeeklyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class WeeklyState(
    val loading: Boolean = true,
    val activity: WeeklyActivity = WeeklyActivity(),
    /** Las semanales del catálogo con su estado detectado. */
    val tasks: List<TaskWithState> = emptyList(),
    /** Semanal desplegada y el botín del contenido que pide. */
    val expandedTaskId: String? = null,
    val lootByTask: Map<String, List<com.azeroth.companion.data.LootEntry>> = emptyMap(),
    /** Misiones concretas de cada semanal, para poder abrir su ficha. */
    val questsByTask: Map<String, List<com.azeroth.companion.data.WeeklyQuestDone>> = emptyMap(),
)

/**
 * "Esta semana": las semanales de la expansión y, debajo, la actividad medida.
 *
 * La lista de semanales sale de las misiones de seguimiento que Blizzard nombra
 * "Midnight: <actividad>", así que cada fila tiene un ID de misión real y se
 * marca sola. Antes eran tareas inventadas con regla ManualOnly y, como la app no
 * tiene entrada manual, se quedaban en 0/N para siempre.
 */
@HiltViewModel
class WeeklyViewModel @Inject constructor(
    private val weeklyActivityRepository: WeeklyActivityRepository,
    private val weeklyRepository: WeeklyRepository,
    private val eventsRepository: EventsRepository,
    private val settingsRepository: SettingsRepository,
    private val activeCharacter: ActiveCharacter,
    private val seasonLootRepository: com.azeroth.companion.data.SeasonLootRepository,
    private val storylinesRepository: com.azeroth.companion.data.StorylinesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WeeklyState())
    val state: StateFlow<WeeklyState> = _state

    /**
     * Al desplegar una semanal se carga el botín del contenido que pide. La
     * misión de seguimiento solo da oro y experiencia: lo que se persigue está
     * en la mazmorra, la banda o el jefe de mundo que hay que completar.
     */
    fun toggleTask(taskId: String) {
        val opening = _state.value.expandedTaskId != taskId
        _state.value = _state.value.copy(expandedTaskId = if (opening) taskId else null)
        if (!opening) return
        val task = _state.value.tasks.firstOrNull { it.task.id == taskId }?.task ?: return
        viewModelScope.launch {
            if (task.lootInstanceIds.isNotEmpty() &&
                !_state.value.lootByTask.containsKey(taskId)
            ) {
                val loot = seasonLootRepository.instanceHighlights(task.lootInstanceIds)
                _state.value = _state.value.copy(
                    lootByTask = _state.value.lootByTask + (taskId to loot),
                )
            }
            if (!_state.value.questsByTask.containsKey(taskId)) {
                // Se enseñan primero las que ya has hecho: son las que explican
                // por qué la fila está marcada.
                val quests = storylinesRepository.questsFor(questIds(task.detectionRule))
                _state.value = _state.value.copy(
                    questsByTask = _state.value.questsByTask + (taskId to quests),
                )
            }
        }
    }

    /** IDs de misión de una regla, incluidas las anidadas en AnyOf/AllOf. */
    private fun questIds(rule: com.azeroth.companion.core.model.DetectionRule): List<Int> = when (rule) {
        is com.azeroth.companion.core.model.DetectionRule.QuestCompleted -> rule.questIds
        is com.azeroth.companion.core.model.DetectionRule.QuestDelta -> rule.questIds
        is com.azeroth.companion.core.model.DetectionRule.AnyOf -> rule.rules.flatMap { questIds(it) }
        is com.azeroth.companion.core.model.DetectionRule.AllOf -> rule.rules.flatMap { questIds(it) }
        else -> emptyList()
    }

    init {
        viewModelScope.launch {
            val activity = weeklyActivityRepository.load()
            _state.value = _state.value.copy(loading = false, activity = activity)
        }
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            // TIENE que ser el mismo personaje con el que el sync guardó los
            // estados: si no hay uno elegido, ambos caen al primero del roster.
            val characterId = activeCharacter.currentId() ?: return@launch
            val tasks = weeklyRepository.tasks(includeLegacy = settings.showLegacyContent)
                .filter { it.category != TaskCategory.GREAT_VAULT }
            val lastReset = eventsRepository.resetClock().lastWeeklyReset(Instant.now())
            weeklyRepository.observeStates(characterId, tasks, lastReset).collect { rows ->
                _state.value = _state.value.copy(
                    tasks = rows.sortedWith(
                        compareByDescending<TaskWithState> { (it.state?.completions ?: 0) > 0 }
                            .thenByDescending { it.task.priorityWeight },
                    ),
                )
            }
        }
    }
}
