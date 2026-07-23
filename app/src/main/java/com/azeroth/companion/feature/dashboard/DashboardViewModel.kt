package com.azeroth.companion.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.model.AuthState
import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.TrackedTask
import com.azeroth.companion.core.network.AuthManager
import com.azeroth.companion.data.EventsRepository
import com.azeroth.companion.data.WeeklyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DashboardState(
    val loading: Boolean = true,
    val nextEventId: String? = null,
    val nextEventName: String = "",
    val nextEventZone: String = "",
    val nextEventStartsAt: Instant? = null,
    val nextEventConfidence: Confidence = Confidence.PREDICTED,
    val weeklyResetAt: Instant? = null,
    val topPending: List<TrackedTask> = emptyList(),
    val authBroken: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val eventsRepository: EventsRepository,
    private val weeklyRepository: WeeklyRepository,
    private val authManager: AuthManager,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                eventsRepository.refreshCalibrations()
                val now = Instant.now()
                val next = eventsRepository.nextOccurrence(now)
                val reset = eventsRepository.resetClock().nextWeeklyReset(now)
                val pending = weeklyRepository.tasks(includeLegacy = false)
                    .sortedByDescending { it.priorityWeight }
                    .take(5)
                eventsRepository.rescheduleEventAlarms()
                _state.value = DashboardState(
                    loading = false,
                    nextEventId = next?.first?.id,
                    nextEventName = next?.first?.name?.get("es_MX")
                        ?: next?.first?.name?.values?.firstOrNull().orEmpty(),
                    nextEventZone = next?.first?.zone.orEmpty(),
                    nextEventStartsAt = next?.second?.startsAt,
                    nextEventConfidence = next?.second?.confidence ?: Confidence.PREDICTED,
                    weeklyResetAt = reset,
                    topPending = pending,
                    authBroken = authManager.state.value is AuthState.Broken,
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }
}
