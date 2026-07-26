package com.azeroth.companion.feature.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.data.Affix
import com.azeroth.companion.data.Boss
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
    val bossesByInstance: Map<Int, List<Boss>> = emptyMap(),
    val lootByBoss: Map<Int, List<com.azeroth.companion.data.LootEntry>> = emptyMap(),
    val showPastExpansions: Boolean = false,
    val error: String? = null,
    /** Jefe al que se ha llegado desde un objeto del botín: se resalta y se abre. */
    val focusBossId: Int = 0,
    val focusInstanceId: Int = 0,
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

    /**
     * Abre directamente la instancia y el jefe de los que cae un objeto. Es lo
     * que hace que tocar una montura en "Exclusivo de la temporada" te lleve a
     * dónde conseguirla, en vez de dejarte buscándola.
     */
    fun focusOn(instanceId: Int, bossId: Int) {
        if (instanceId == 0 && bossId == 0) return
        viewModelScope.launch {
            // Puede llegar antes de que termine refresh(): se espera a tener
            // contenido para que la instancia exista en la lista.
            while (_state.value.loading) kotlinx.coroutines.delay(50)
            _state.value = _state.value.copy(focusInstanceId = instanceId, focusBossId = bossId)
            if (instanceId != 0) loadBosses(instanceId)
            if (bossId != 0) loadLoot(bossId, isRaid = _state.value.expansion?.raids
                ?.any { it.id == instanceId } == true)
        }
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

    /** Botín de un jefe al tocarlo (¿qué looteo aquí?). */
    fun loadLoot(encounterId: Int, isRaid: Boolean = true) {
        if (_state.value.lootByBoss.containsKey(encounterId)) return
        viewModelScope.launch {
            val loot = contentRepository.bossLoot(encounterId, isRaid)
            _state.value = _state.value.copy(
                lootByBoss = _state.value.lootByBoss + (encounterId to loot),
            )
        }
    }
}
