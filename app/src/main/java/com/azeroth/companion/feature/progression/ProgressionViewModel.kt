package com.azeroth.companion.feature.progression

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.catalog.CatalogRepository
import com.azeroth.companion.core.catalog.EconomyRules
import com.azeroth.companion.core.database.ProgressionStateEntity
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.vault.VaultCalculator
import com.azeroth.companion.data.ProgressionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressionUiState(
    val loading: Boolean = true,
    val characterId: Long = 0L,
    val folioRows: Int = 0,
    val folioTotalRows: Int = 5,
    val folioCatchUp: Int = 0,
    val prey: Map<String, Int> = emptyMap(),
    val delveKeys: Int = 0,
    val delvesDone: Int = 0,
    val crestsThisWeek: Int = 0,
    val crestsTotal: Int = 0,
    val economy: EconomyRules = EconomyRules(),
    val upgradePlan: VaultCalculator.UpgradePlan = VaultCalculator.UpgradePlan(0, 0, 0, 0),
)

@HiltViewModel
class ProgressionViewModel @Inject constructor(
    private val progressionRepository: ProgressionRepository,
    private val settingsRepository: SettingsRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressionUiState())
    val state: StateFlow<ProgressionUiState> = _state

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val characterId = settings.activeCharacterId ?: 0L
            val economy = catalogRepository.load().economy
            progressionRepository.observe(characterId).collect { entity ->
                val s = entity ?: ProgressionStateEntity(characterId = characterId)
                _state.value = ProgressionUiState(
                    loading = false,
                    characterId = characterId,
                    folioRows = s.folioUnlockedRows,
                    folioCatchUp = s.folioCatchUpPending,
                    prey = progressionRepository.preyProgress(s),
                    delveKeys = s.delveKeysAvailable,
                    delvesDone = s.delvesDoneThisWeek,
                    crestsThisWeek = s.crestsThisWeek,
                    crestsTotal = s.crestsTotal,
                    economy = economy,
                    upgradePlan = VaultCalculator.upgradePlan(s.crestsTotal, economy),
                )
            }
        }
    }

    fun setFolioRows(rows: Int) = mutate {
        it.copy(
            folioUnlockedRows = rows.coerceIn(0, 5),
            // Catch-up secuencial (§7.3): filas de semanas pasadas aún no hechas.
            folioCatchUpPending = (it.folioCatchUpPending).coerceAtLeast(0),
        )
    }

    fun setFolioCatchUp(pending: Int) = mutate { it.copy(folioCatchUpPending = pending.coerceIn(0, 5)) }

    fun setPrey(zone: String, percent: Int) {
        viewModelScope.launch {
            progressionRepository.setPreyProgress(_state.value.characterId, zone, percent)
        }
    }

    fun setDelveKeys(keys: Int) = mutate { it.copy(delveKeysAvailable = keys.coerceIn(0, 20)) }
    fun setDelvesDone(done: Int) = mutate { it.copy(delvesDoneThisWeek = done.coerceIn(0, 20)) }
    fun setCrestsTotal(total: Int) = mutate { it.copy(crestsTotal = total.coerceIn(0, 999)) }
    fun setCrestsThisWeek(week: Int) = mutate { it.copy(crestsThisWeek = week.coerceIn(0, 999)) }

    private fun mutate(transform: (ProgressionStateEntity) -> ProgressionStateEntity) {
        viewModelScope.launch {
            val current = progressionRepository.getOrDefault(_state.value.characterId)
            progressionRepository.update(transform(current))
        }
    }
}
