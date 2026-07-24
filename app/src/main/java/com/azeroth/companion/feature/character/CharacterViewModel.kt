package com.azeroth.companion.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.database.CharacterEntity
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.data.CharacterDetail
import com.azeroth.companion.data.CharacterDetailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CharacterUiState(
    val loading: Boolean = true,
    val roster: List<CharacterEntity> = emptyList(),
    val selected: CharacterEntity? = null,
    val detail: CharacterDetail? = null,
    val detailLoading: Boolean = false,
)

@HiltViewModel
class CharacterViewModel @Inject constructor(
    private val repository: CharacterDetailRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CharacterUiState())
    val state: StateFlow<CharacterUiState> = _state

    init {
        viewModelScope.launch {
            val activeId = settingsRepository.settings.first().activeCharacterId
            repository.roster().collect { roster ->
                val selected = _state.value.selected
                    ?: roster.firstOrNull { it.id == activeId }
                    ?: roster.firstOrNull()
                _state.value = _state.value.copy(loading = false, roster = roster, selected = selected)
                if (selected != null && _state.value.detail == null) loadDetail(selected)
            }
        }
    }

    fun select(character: CharacterEntity) {
        _state.value = _state.value.copy(selected = character, detail = null)
        viewModelScope.launch { settingsRepository.setActiveCharacter(character.id) }
        loadDetail(character)
    }

    private fun loadDetail(character: CharacterEntity) {
        viewModelScope.launch {
            _state.value = _state.value.copy(detailLoading = true)
            val detail = repository.detail(character)
            _state.value = _state.value.copy(detail = detail, detailLoading = false)
        }
    }
}
