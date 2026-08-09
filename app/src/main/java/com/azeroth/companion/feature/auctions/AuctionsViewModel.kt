package com.azeroth.companion.feature.auctions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.data.AuctionListing
import com.azeroth.companion.data.AuctionRepository
import com.azeroth.companion.data.AuctionScope
import com.azeroth.companion.data.AuctionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuctionTab { EXPENSIVE, TRADED, SEARCH }

data class AuctionsUiState(
    val scope: AuctionScope = AuctionScope.COMMODITIES,
    val tab: AuctionTab = AuctionTab.EXPENSIVE,
    val query: String = "",
    val rows: List<AuctionListing> = emptyList(),
    val status: AuctionStatus? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuctionsViewModel @Inject constructor(
    private val repository: AuctionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuctionsUiState())
    val state: StateFlow<AuctionsUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        reload()
    }

    fun selectScope(scope: AuctionScope) {
        if (scope == _state.value.scope) return
        _state.update { it.copy(scope = scope, rows = emptyList()) }
        reload()
    }

    fun selectTab(tab: AuctionTab) {
        if (tab == _state.value.tab) return
        _state.update { it.copy(tab = tab) }
        reload()
    }

    fun search(query: String) {
        _state.update { it.copy(query = query, tab = AuctionTab.SEARCH) }
        // Un rebote corto: buscar en cada pulsación recorrería 25.000 nombres
        // por letra y se notaría al escribir.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(220)
            reload()
        }
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            val scope = _state.value.scope
            repository.refresh(scope)
                .onSuccess { _state.update { s -> s.copy(refreshing = false) } }
                .onFailure { error ->
                    _state.update { s -> s.copy(refreshing = false, error = error.message) }
                }
            reload()
        }
    }

    private fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val current = _state.value
            val rows = runCatching {
                when (current.tab) {
                    AuctionTab.EXPENSIVE -> repository.mostExpensive(current.scope)
                    AuctionTab.TRADED -> repository.mostTraded(current.scope)
                    AuctionTab.SEARCH -> repository.search(current.scope, current.query)
                }
            }.getOrDefault(emptyList())
            val status = runCatching { repository.status(current.scope) }.getOrNull()
            _state.update { it.copy(rows = rows, status = status, loading = false) }
        }
    }
}
