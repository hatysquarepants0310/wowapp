package com.azeroth.companion.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.data.LiveEvent
import com.azeroth.companion.data.LiveMapRepository
import com.azeroth.companion.data.LiveZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveMapUiState(
    val zones: List<LiveZone> = emptyList(),
    val events: List<LiveEvent> = emptyList(),
    val characterName: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class LiveMapViewModel @Inject constructor(
    private val repository: LiveMapRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveMapUiState())
    val state: StateFlow<LiveMapUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val snapshot = runCatching { repository.snapshot() }.getOrNull()
            _state.update {
                if (snapshot == null) {
                    it.copy(loading = false, error = "No se pudo leer el estado del mundo.")
                } else {
                    LiveMapUiState(
                        zones = snapshot.zones,
                        events = snapshot.events,
                        characterName = snapshot.characterName,
                        loading = false,
                        error = snapshot.error,
                    )
                }
            }
        }
    }
}
