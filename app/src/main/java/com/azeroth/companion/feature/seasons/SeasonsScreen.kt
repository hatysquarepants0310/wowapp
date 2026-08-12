package com.azeroth.companion.feature.seasons

import androidx.compose.foundation.layout.PaddingValues
import com.azeroth.companion.ui.components.Panel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.azeroth.companion.ui.components.WowLoading
import com.azeroth.companion.data.SeasonProgress
import com.azeroth.companion.data.SeasonsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeasonsUiState(val loading: Boolean = true, val seasons: List<SeasonProgress> = emptyList())

@HiltViewModel
class SeasonsViewModel @Inject constructor(
    private val repository: SeasonsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SeasonsUiState())
    val state: StateFlow<SeasonsUiState> = _state

    init {
        viewModelScope.launch {
            _state.value = SeasonsUiState(loading = false, seasons = repository.seasons())
        }
    }
}

/**
 * Temporadas: tu progreso de Mythic+ por temporada y en cuáles participaste.
 * Con sesión activa muestra tu rating, mejor nivel y número de runs por temporada.
 */
@Composable
fun SeasonsScreen(viewModel: SeasonsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) { WowLoading() }
        return
    }
    if (state.seasons.isEmpty()) {
        Text("No se pudieron cargar las temporadas. Revisa tu conexión.",
            Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Historial de temporadas de Mythic+", style = MaterialTheme.typography.titleMedium)
            Text("Con sesión activa se muestra tu rating y mejor llave por temporada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(state.seasons, key = { it.seasonId }) { season -> SeasonCard(season) }
    }
}

@Composable
private fun SeasonCard(season: SeasonProgress) {
    Panel(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Temporada ${season.seasonId}" + if (season.isCurrent) " · ACTUAL" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (season.isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface)
                if (season.participated) {
                    Text("✓ Participaste", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Sin runs", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (season.participated) {
                Text(
                    "Rating ${season.rating} · Mejor llave +${season.bestLevel} · ${season.runCount} runs registradas",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
