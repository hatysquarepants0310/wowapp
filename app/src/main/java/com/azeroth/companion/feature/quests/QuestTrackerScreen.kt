package com.azeroth.companion.feature.quests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.azeroth.companion.data.QuestEntry
import com.azeroth.companion.data.QuestTrackerRepository
import com.azeroth.companion.data.QuestZone
import com.azeroth.companion.data.ZoneQuests
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuestTrackerState(
    val loading: Boolean = true,
    val zones: List<QuestZone> = emptyList(),
    val query: String = "",
    val selectedZone: ZoneQuests? = null,
    val zoneLoading: Boolean = false,
)

@HiltViewModel
class QuestTrackerViewModel @Inject constructor(
    private val repository: QuestTrackerRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(QuestTrackerState())
    val state: StateFlow<QuestTrackerState> = _state

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = false, zones = repository.zones())
        }
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(query = q) }

    fun openZone(zone: QuestZone) {
        viewModelScope.launch {
            _state.value = _state.value.copy(zoneLoading = true)
            val zq = repository.zoneQuests(zone.id, zone.name)
            _state.value = _state.value.copy(selectedZone = zq, zoneLoading = false)
        }
    }

    fun back() { _state.value = _state.value.copy(selectedZone = null) }
}

@Composable
fun QuestTrackerScreen(viewModel: QuestTrackerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.zoneLoading -> Center { CircularProgressIndicator() }
        state.selectedZone != null -> ZoneDetail(state.selectedZone!!, viewModel::back)
        state.loading -> Center { CircularProgressIndicator() }
        else -> ZoneList(state, viewModel)
    }
}

@Composable
private fun ZoneList(state: QuestTrackerState, viewModel: QuestTrackerViewModel) {
    val filtered = state.zones.filter {
        state.query.isBlank() || it.name.contains(state.query, ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            label = { Text("Buscar zona") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text("${state.zones.size} zonas · toca una para ver tus misiones",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp))
        if (state.zones.isEmpty()) {
            Text("No se pudieron cargar las zonas. Revisa tu conexión.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { zone ->
                Card(Modifier.fillMaxWidth().clickable { viewModel.openZone(zone) }) {
                    Text(zone.name, Modifier.padding(14.dp),
                        style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun ZoneDetail(zone: ZoneQuests, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Zonas") }
        Text(zone.zoneName, style = MaterialTheme.typography.headlineSmall)
        if (zone.total > 0) {
            Text("${zone.completedCount} / ${zone.total} misiones completadas",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(
                progress = { zone.completedCount.toFloat() / zone.total },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
        if (!zone.hasAccountData) {
            Text("Inicia sesión con Battle.net y sincroniza para ver tu progreso real. " +
                "Ahora se muestra el listado completo de la zona.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        if (zone.quests.isEmpty()) {
            Text("Sin misiones listadas para esta zona en la API.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(zone.quests, key = { it.id }) { quest -> QuestRow(quest) }
        }
    }
}

@Composable
private fun QuestRow(quest: QuestEntry) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (quest.completed) "✓" else "○",
            style = MaterialTheme.typography.titleMedium,
            color = if (quest.completed) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold)
        Text(quest.name, style = MaterialTheme.typography.bodyMedium,
            color = if (quest.completed) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) { content() }
}
