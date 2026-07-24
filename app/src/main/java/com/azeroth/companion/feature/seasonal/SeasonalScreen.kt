package com.azeroth.companion.feature.seasonal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import com.azeroth.companion.core.database.CharacterDao
import com.azeroth.companion.core.database.SeasonalGoalDao
import com.azeroth.companion.core.database.SeasonalGoalEntity
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.model.SeasonalReward
import com.azeroth.companion.core.model.Viability
import com.azeroth.companion.ui.components.CountdownText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class SeasonalRow(
    val reward: SeasonalReward,
    val viability: Viability,
    val targeted: Boolean,
    val obtained: Boolean,
)

data class SeasonalState(
    val rows: List<SeasonalRow> = emptyList(),
    val filterEnabled: Boolean = false,
    /** ilvl del personaje activo sincronizado; 0 = sin dato. */
    val playerItemLevel: Int = 0,
    val seasonName: String = "",
    val seasonEndsAt: Instant? = null,
)

@HiltViewModel
class SeasonalViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val seasonalGoalDao: SeasonalGoalDao,
    characterDao: CharacterDao,
) : ViewModel() {

    private val _state = MutableStateFlow(SeasonalState())
    val state: StateFlow<SeasonalState> = _state

    init {
        viewModelScope.launch {
            val catalog = catalogRepository.load()
            val endsAt = catalog.season.endEstimateUtc
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            combine(
                settingsRepository.settings,
                seasonalGoalDao.observeAll(),
                characterDao.observeAll(),
            ) { settings, goals, characters ->
                val goalsById = goals.associateBy { it.rewardId }
                val ilvl = characters.firstOrNull()?.equippedItemLevel ?: 0
                SeasonalState(
                    rows = catalog.seasonalRewards.map { reward ->
                        val goal = goalsById[reward.id]
                        SeasonalRow(
                            reward = reward,
                            viability = classify(reward, ilvl),
                            targeted = goal?.targeted ?: false,
                            obtained = goal?.obtained ?: false,
                        )
                    },
                    filterEnabled = settings.viabilityFilterEnabled,
                    playerItemLevel = ilvl,
                    seasonName = catalog.season.name["es_MX"]
                        ?: catalog.season.name.values.firstOrNull().orEmpty(),
                    seasonEndsAt = endsAt,
                )
            }.collect { _state.value = it }
        }
    }

    fun setFilter(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setViabilityFilter(enabled) }
    }

    /**
     * Filtro de viabilidad (§8.2): un recién llegado a nivel máximo no debe ver
     * el Cutting Edge Mítico como "pendiente". Con ilvl sincronizado la
     * clasificación es real; sin dato, prudencia (TIGHT), nunca falso optimismo.
     */
    private fun classify(reward: SeasonalReward, playerIlvl: Int): Viability {
        val required = reward.realisticForItemLevel ?: return Viability.ACHIEVABLE
        return when {
            playerIlvl <= 0 -> Viability.TIGHT
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
        state.seasonEndsAt?.let { end ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${state.seasonName} — cierre estimado",
                        style = MaterialTheme.typography.titleSmall)
                    CountdownText(end, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Text(
            stringResource(R.string.season_deadline_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.playerItemLevel > 0) {
            Text(
                "Viabilidad calculada con tu ilvl sincronizado (${state.playerItemLevel}). " +
                    "Lo conseguido se marca solo al sincronizar tus logros y monturas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text("Ocultar objetivos no realistas")
            Switch(checked = state.filterEnabled, onCheckedChange = viewModel::setFilter)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val visible = state.rows.filter {
                !state.filterEnabled || it.viability != Viability.UNREALISTIC || it.targeted
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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                row.obtained -> "✓ Conseguido"
                                row.viability == Viability.ACHIEVABLE -> "Alcanzable"
                                row.viability == Viability.TIGHT -> "Ajustado"
                                else -> "No realista en el tiempo restante"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (row.obtained) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }
}
