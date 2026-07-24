package com.azeroth.companion.feature.weekly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.model.TaskCategory
import com.azeroth.companion.data.EventsRepository
import com.azeroth.companion.data.TaskWithState
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
    val groups: Map<TaskCategory, List<TaskWithState>> = emptyMap(),
    val characterId: Long = LOCAL_PROFILE_ID,
) {
    companion object {
        /** Perfil local sin sesión de Battle.net: la checklist funciona igual (§0.2). */
        const val LOCAL_PROFILE_ID = 0L
    }
}

@HiltViewModel
class WeeklyViewModel @Inject constructor(
    private val weeklyRepository: WeeklyRepository,
    private val eventsRepository: EventsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WeeklyState())
    val state: StateFlow<WeeklyState> = _state

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val characterId = settings.activeCharacterId ?: WeeklyState.LOCAL_PROFILE_ID
            val tasks = weeklyRepository.tasks(includeLegacy = settings.showLegacyContent)
            val lastReset = eventsRepository.resetClock().lastWeeklyReset(Instant.now())
            weeklyRepository.observeStates(characterId, tasks, lastReset).collect { rows ->
                _state.value = WeeklyState(
                    loading = false,
                    groups = rows.groupBy { it.task.category },
                    characterId = characterId,
                )
            }
        }
    }

}
