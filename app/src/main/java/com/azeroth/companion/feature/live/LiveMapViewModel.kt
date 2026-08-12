package com.azeroth.companion.feature.live

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.map.ZoneMapLoader
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
    /** Mapa del juego ya compuesto, por uiMapId. */
    val maps: Map<Int, Bitmap> = emptyMap(),
    /** Quedan zonas por traer; la pantalla lo indica sin bloquear nada. */
    val loadingMaps: Boolean = false,
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
     * Antes había que entrar en cada zona y pulsar actualizar, una por una, que
     * es justo lo que nadie quiere hacer. Ahora se descargan solas nada más
     * cargar la pantalla: la primera se compone ya, para que haya algo que
     * mirar, y el resto va llegando de fondo.
     */
    private fun loadAllMaps(uiMapIds: List<Int>) {
        if (uiMapIds.isEmpty()) return
        _state.update { it.copy(loadingMaps = true) }
        viewModelScope.launch {
            uiMapIds.firstOrNull()?.let { first ->
                mapLoader.load(first)?.let { bitmap ->
                    _state.update { it.copy(maps = it.maps + (first to bitmap)) }
                }
            }
            val rest = uiMapIds.drop(1)
            // A disco en paralelo primero: componer va mucho más rápido cuando
            // las texturas ya están, y así ninguna zona espera a la red sola.
            mapLoader.prefetch(rest)
            rest.forEach { id ->
                mapLoader.load(id)?.let { bitmap ->
                    _state.update { it.copy(maps = it.maps + (id to bitmap)) }
                }
            }
            _state.update { it.copy(loadingMaps = false) }
        }
    }
}
