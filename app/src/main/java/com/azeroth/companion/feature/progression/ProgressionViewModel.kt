package com.azeroth.companion.feature.progression

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.catalog.CatalogRepository
import com.azeroth.companion.core.catalog.EconomyRules
import com.azeroth.companion.core.database.CharacterDao
import com.azeroth.companion.core.model.GreatVaultProgress
import com.azeroth.companion.data.ProgressionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressionUiState(
    val loading: Boolean = true,
    val vault: GreatVaultProgress? = null,
    val economy: EconomyRules = EconomyRules(),
    val synced: Boolean = false,
)

/**
 * Progresión 100% automática: todo sale del sync con Battle.net. Lo que la API
 * de Blizzard no expone (Folio, % de Presas) se muestra como guía informativa,
 * nunca como campo a rellenar.
 */
@HiltViewModel
class ProgressionViewModel @Inject constructor(
    private val progressionRepository: ProgressionRepository,
    private val catalogRepository: CatalogRepository,
    private val characterDao: CharacterDao,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressionUiState())
    val state: StateFlow<ProgressionUiState> = _state

    init {
        viewModelScope.launch {
            runCatching {
                val economy = catalogRepository.load().economy
                val active = characterDao.observeAll().first().firstOrNull()
                val vault = active?.let { progressionRepository.computeVault(it.id) }
                _state.value = ProgressionUiState(
                    loading = false,
                    vault = vault,
                    economy = economy,
                    synced = active?.lastSyncedAt != null,
                )
            }.onFailure { _state.value = ProgressionUiState(loading = false) }
        }
    }
}
