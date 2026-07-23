package com.azeroth.companion.feature.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.model.EventOccurrence
import com.azeroth.companion.core.model.WorldEventDefinition
import com.azeroth.companion.data.EventsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class EventRow(val definition: WorldEventDefinition, val next: EventOccurrence?)

data class EventsState(
    val loading: Boolean = true,
    val rows: List<EventRow> = emptyList(),
    val calibrationMessage: String? = null,
)

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventsRepository: EventsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EventsState())
    val state: StateFlow<EventsState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                eventsRepository.refreshCalibrations()
                val scheduler = eventsRepository.scheduler()
                val now = Instant.now()
                val rows = eventsRepository.events()
                    .map { EventRow(it, scheduler.nextOccurrence(it, now)) }
                    .sortedBy { it.next?.startsAt ?: Instant.MAX }
                _state.value = EventsState(loading = false, rows = rows)
            }.onFailure { _state.value = EventsState(loading = false) }
        }
    }

    /** Botón "El evento acaba de empezar" (§4.4). */
    fun markEventJustStarted(eventId: String) {
        viewModelScope.launch {
            eventsRepository.recordEventStartObservation(eventId)
            _state.value = _state.value.copy(
                calibrationMessage = "Observación registrada. Con 3+ observaciones el horario se calibra solo.",
            )
            refresh()
        }
    }

    fun eventById(id: String): WorldEventDefinition? =
        _state.value.rows.firstOrNull { it.definition.id == id }?.definition
}
