package com.azeroth.companion.feature.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.data.Affix
import com.azeroth.companion.data.ContentRepository
import com.azeroth.companion.data.ExpansionContent
import com.azeroth.companion.data.ExpansionRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContentState(
    val loading: Boolean = true,
    val affixTitle: String = "",
    val affixes: List<Affix> = emptyList(),
    val expansions: List<ExpansionRef> = emptyList(),
    val selectedExpansionId: Int = 0,
    val expansion: ExpansionContent? = null,
    val bossesByInstance: Map<Int, List<String>> = emptyMap(),
    val showPastExpansions: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ContentViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ContentState())
    val state: StateFlow<ContentState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val affixes = contentRepository.currentAffixes()
            val expansions = contentRepository.allExpansions()
            val currentId = expansions.firstOrNull { it.isCurrent }?.id
                ?: contentRepository.currentExpansionId()
            val expansion = contentRepository.expansionContent(currentId)
            _state.value = _state.value.copy(
                loading = false,
                affixTitle = affixes?.first.orEmpty(),
                affixes = affixes?.second ?: emptyList(),
                expansions = expansions,
                selectedExpansionId = currentId,
                expansion = expansion,
                bossesByInstance = emptyMap(),
                error = if (affixes == null && expansion == null)
                    "No se pudo cargar el contenido. Revisa tu conexión y reintenta." else null,
            )
        }
    }

    fun selectExpansion(id: Int) {
        if (id == _state.value.selectedExpansionId) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val expansion = contentRepository.expansionContent(id)
            _state.value = _state.value.copy(
                loading = false,
                selectedExpansionId = id,
                expansion = expansion,
                bossesByInstance = emptyMap(),
            )
        }
    }

    fun togglePastExpansions() {
        _state.value = _state.value.copy(showPastExpansions = !_state.value.showPastExpansions)
    }

    fun loadBosses(instanceId: Int) {
        if (_state.value.bossesByInstance.containsKey(instanceId)) return
        viewModelScope.launch {
            val bosses = contentRepository.bosses(instanceId)
            _state.value = _state.value.copy(
                bossesByInstance = _state.value.bossesByInstance + (instanceId to bosses),
            )
        }
    }
}
