package com.azeroth.companion.feature.seasonal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.R
import com.azeroth.companion.core.catalog.CatalogRepository
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.model.SeasonalReward
import com.azeroth.companion.core.model.Viability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeasonalRow(val reward: SeasonalReward, val viability: Viability)

data class SeasonalState(
    val rows: List<SeasonalRow> = emptyList(),
    val filterEnabled: Boolean = false,
    /** ilvl del personaje; manual mientras no haya sesión (modo degradado). */
    val playerItemLevel: Int = 0,
)

@HiltViewModel
class SeasonalViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SeasonalState())
    val state: StateFlow<SeasonalState> = _state

    init {
        viewModelScope.launch {
            val rewards = catalogRepository.load().seasonalRewards
            settingsRepository.settings.collect { settings ->
                val ilvl = _state.value.playerItemLevel
                _state.value = SeasonalState(
                    rows = rewards.map { SeasonalRow(it, classify(it, ilvl)) },
                    filterEnabled = settings.viabilityFilterEnabled,
                    playerItemLevel = ilvl,
                )
            }
        }
    }

    fun setFilter(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setViabilityFilter(enabled) }
    }

    /**
     * Filtro de viabilidad (§8.2): un recién llegado a nivel máximo no debe
     * ver el Cutting Edge Mítico como "pendiente".
     */
    private fun classify(reward: SeasonalReward, playerIlvl: Int): Viability {
        val required = reward.realisticForItemLevel ?: return Viability.ACHIEVABLE
        return when {
            playerIlvl <= 0 -> Viability.TIGHT // sin dato: prudencia, no falso optimismo
            playerIlvl >= required -> Viability.ACHIEVABLE
            playerIlvl >= required - 15 -> Viability.TIGHT
            else -> Viability.UNREALISTIC
        }
    }
}

@Composable
fun SeasonalScreen(viewModel: SeasonalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.season_deadline_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text("Ocultar objetivos no realistas")
            Switch(checked = state.filterEnabled, onCheckedChange = viewModel::setFilter)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val visible = state.rows.filter {
                !state.filterEnabled || it.viability != Viability.UNREALISTIC
            }
            items(visible, key = { it.reward.id }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(row.reward.name["es_MX"] ?: row.reward.name.values.first(),
                            style = MaterialTheme.typography.titleSmall)
                        row.reward.source["es_MX"]?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            when (row.viability) {
                                Viability.ACHIEVABLE -> "Alcanzable"
                                Viability.TIGHT -> "Ajustado"
                                Viability.UNREALISTIC -> "No realista en el tiempo restante"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }
}
