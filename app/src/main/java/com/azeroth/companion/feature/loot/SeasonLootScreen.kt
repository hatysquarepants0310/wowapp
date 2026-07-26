package com.azeroth.companion.feature.loot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.data.LootEntry
import com.azeroth.companion.data.SeasonLootRepository
import com.azeroth.companion.ui.components.LootRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeasonLootState(
    val loading: Boolean = true,
    val expansion: String = "",
    val mounts: List<LootEntry> = emptyList(),
    val gear: List<LootEntry> = emptyList(),
)

@HiltViewModel
class SeasonLootViewModel @Inject constructor(
    private val repository: SeasonLootRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SeasonLootState())
    val state: StateFlow<SeasonLootState> = _state

    init {
        viewModelScope.launch {
            _state.value = SeasonLootState(
                loading = false,
                expansion = repository.expansionName(),
                mounts = repository.seasonMounts(),
                gear = repository.highlightGear(),
            )
        }
    }
}

/**
 * Lo exclusivo de la temporada: monturas y equipo destacado, con imagen, de qué
 * jefe salen, en qué dificultades y la probabilidad estimada. Sale del journal
 * oficial horneado en assets, así que abre al instante y sin conexión.
 */
@Composable
fun SeasonLootScreen(viewModel: SeasonLootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                "Exclusivo de ${state.expansion}. Las probabilidades son estimadas: " +
                    "Blizzard publica la tabla de botín de cada jefe, pero no las tasas " +
                    "de caída. Cada línea dice de dónde sale su número.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }

        if (state.mounts.isNotEmpty()) {
            item {
                SectionTitle("Monturas de la temporada", state.mounts.size)
            }
            items(state.mounts, key = { "m_${it.itemId}" }) { LootRow(it) }
            item { Spacer(Modifier.height(8.dp)); HorizontalDivider() }
        }

        item { SectionTitle("Equipo destacado (jefes finales)", state.gear.size) }
        if (state.gear.isEmpty()) {
            item {
                Text(
                    "Sin datos de botín para esta expansión todavía.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.gear, key = { "g_${it.itemId}" }) { LootRow(it) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
