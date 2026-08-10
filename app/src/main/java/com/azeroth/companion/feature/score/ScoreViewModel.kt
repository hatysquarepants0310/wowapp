package com.azeroth.companion.feature.score

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.data.ActiveCharacter
import com.azeroth.companion.data.RaiderIoProfile
import com.azeroth.companion.data.RaiderIoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScoreUiState(
    val profile: RaiderIoProfile? = null,
    val characterName: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ScoreViewModel @Inject constructor(
    private val repository: RaiderIoRepository,
    private val activeCharacter: ActiveCharacter,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScoreUiState())
    val state: StateFlow<ScoreUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val character = activeCharacter.current()
            if (character == null) {
                _state.update {
                    it.copy(loading = false, error = "Sincroniza tu roster primero.")
                }
                return@launch
            }
            val region = settingsRepository.settings.first().region
            repository.profile(region, character.realmSlug, character.name)
                .onSuccess { profile ->
                    _state.update {
                        it.copy(
                            profile = profile,
                            characterName = character.name,
                            loading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            characterName = character.name,
                            loading = false,
                            error = error.message,
                        )
                    }
                }
        }
    }
}
