package com.azeroth.companion.feature.live

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.map.ZoneMapFailReason
import com.azeroth.companion.core.map.ZoneMapLoadResult
import com.azeroth.companion.core.map.ZoneMapLoader
import com.azeroth.companion.data.LiveEvent
import com.azeroth.companion.data.LiveMapRepository
import com.azeroth.companion.data.LiveZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
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
    /** Mapa del juego ya compuesto, por uiMapId. */
    val maps: Map<Int, Bitmap> = emptyMap(),
    /** Quedan zonas por traer; la pantalla lo indica sin bloquear nada. */
    val loadingMaps: Boolean = false,
    /** Tile que falló al cargar o decodificar. Ausencia = todavía no se sabe. */
    val mapErrors: Map<Int, ZoneMapFailReason> = emptyMap(),
    /** Arte desactivado o no pedido: la rejilla, no un error. */
    val mapSkipped: Set<Int> = emptySet(),
)

@HiltViewModel
class LiveMapViewModel @Inject constructor(
    private val repository: LiveMapRepository,
    private val mapLoader: ZoneMapLoader,
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
                    it.copy(
                        zones = snapshot.zones,
                        events = snapshot.events,
                        characterName = snapshot.characterName,
                        loading = false,
                        error = snapshot.error,
                    )
                }
            }
            snapshot?.zones?.map { it.uiMapId }?.let(::loadAllMaps)
        }
    }

    /**
     * Trae el arte de TODAS las zonas del jugador en cuanto se sabe cuáles son.
     *
     * El snapshot (pines + lista) ya está en pantalla: esto no lo bloquea.
     * La zona visible se va pintando tile a tile (caché primero); el resto
     * se prefetch a disco y se compone de fondo.
     */
    private fun loadAllMaps(uiMapIds: List<Int>) {
        if (uiMapIds.isEmpty()) return
        _state.update {
            it.copy(
                loadingMaps = true,
                maps = emptyMap(),
                mapErrors = emptyMap(),
                mapSkipped = emptySet(),
            )
        }
        viewModelScope.launch {
            try {
                val first = uiMapIds.first()
                val rest = uiMapIds.drop(1)
                coroutineScope {
                    launch {
                        mapLoader.loadProgress(first).collect { applyMapResult(first, it) }
                    }
                    launch {
                        mapLoader.prefetch(rest)
                        rest.forEach { id ->
                            mapLoader.loadProgress(id).collect { applyMapResult(id, it) }
                        }
                    }
                }
            } finally {
                _state.update { it.copy(loadingMaps = false) }
            }
        }
    }

    private fun applyMapResult(uiMapId: Int, result: ZoneMapLoadResult) {
        _state.update { state ->
            when (result) {
                is ZoneMapLoadResult.Ready -> state.copy(
                    maps = state.maps + (uiMapId to result.bitmap),
                    mapErrors = state.mapErrors - uiMapId,
                    mapSkipped = state.mapSkipped - uiMapId,
                )
                is ZoneMapLoadResult.Skipped -> state.copy(
                    mapSkipped = state.mapSkipped + uiMapId,
                    mapErrors = state.mapErrors - uiMapId,
                )
                is ZoneMapLoadResult.Failed -> state.copy(
                    maps = state.maps - uiMapId,
                    mapErrors = state.mapErrors + (uiMapId to result.reason),
                    mapSkipped = state.mapSkipped - uiMapId,
                )
            }
        }
    }
}
