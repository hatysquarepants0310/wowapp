package com.azeroth.companion.feature.weekly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.model.TaskCategory
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
) : ViewModel() {

    private val _state = MutableStateFlow(WeeklyState())
    val state: StateFlow<WeeklyState> = _state

    init {
        viewModelScope.launch {
            val activity = weeklyActivityRepository.load()
            _state.value = _state.value.copy(loading = false, activity = activity)
        }
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val characterId = settings.activeCharacterId ?: 0L
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
