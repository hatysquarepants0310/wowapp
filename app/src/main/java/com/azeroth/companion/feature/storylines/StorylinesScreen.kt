package com.azeroth.companion.feature.storylines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.azeroth.companion.data.StorylineProgress
import com.azeroth.companion.data.StorylineQuest
import com.azeroth.companion.data.StorylinesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StorylineFilter(val label: String) {
    CURRENT("Temporada actual"),
    CAMPAIGN("Campañas"),
    ALL("Todas"),
}

data class StorylinesState(
    val loading: Boolean = true,
    val hasAccount: Boolean = false,
    val all: List<StorylineProgress> = emptyList(),
    val query: String = "",
    val onlyStarted: Boolean = false,
    val filter: StorylineFilter = StorylineFilter.CURRENT,
    val selected: StorylineProgress? = null,
    val selectedQuests: List<StorylineQuest> = emptyList(),
    val detailLoading: Boolean = false,
)

@HiltViewModel
class StorylinesViewModel @Inject constructor(
    private val repository: StorylinesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(StorylinesState())
    val state: StateFlow<StorylinesState> = _state

    init {
        viewModelScope.launch {
            val (hasAccount, list) = repository.storylines()
            _state.value = _state.value.copy(loading = false, hasAccount = hasAccount, all = list)
        }
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(query = q) }
    fun toggleStarted() { _state.value = _state.value.copy(onlyStarted = !_state.value.onlyStarted) }
    fun setFilter(f: StorylineFilter) { _state.value = _state.value.copy(filter = f) }

    fun open(story: StorylineProgress) {
        _state.value = _state.value.copy(selected = story, selectedQuests = emptyList(), detailLoading = true)
        viewModelScope.launch {
            val quests = repository.storylineQuests(story.id)
            _state.value = _state.value.copy(selectedQuests = quests, detailLoading = false)
        }
    }

    fun back() { _state.value = _state.value.copy(selected = null, selectedQuests = emptyList()) }
}

/**
 * Historias (storylines) de WoW: cada cadena de misiones con tu progreso real,
 * como en Wowhead. Toca una para ver sus misiones, dónde y qué dan.
 */
@Composable
fun StorylinesScreen(viewModel: StorylinesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.selected != null -> StorylineDetail(state, viewModel)
        state.loading -> Center { CircularProgressIndicator() }
        else -> StorylineList(state, viewModel)
    }
}

@Composable
private fun StorylineList(state: StorylinesState, viewModel: StorylinesViewModel) {
    val filtered = state.all.filter {
        (state.query.isBlank() || it.name.contains(state.query, ignoreCase = true) ||
            (it.zone?.contains(state.query, ignoreCase = true) == true)) &&
            (!state.onlyStarted || it.completed > 0) &&
            when (state.filter) {
                StorylineFilter.CURRENT -> it.currentExpansion
                StorylineFilter.CAMPAIGN -> it.campaign
                StorylineFilter.ALL -> true
            }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            label = { Text("Buscar historia o zona") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StorylineFilter.entries.forEach { f ->
                androidx.compose.material3.FilterChip(
                    selected = state.filter == f,
                    onClick = { viewModel.setFilter(f) },
                    label = { Text(f.label) },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("${filtered.size} historias",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = viewModel::toggleStarted) {
                Text(if (state.onlyStarted) "Ver todas" else "Solo empezadas")
            }
        }
        if (filtered.isEmpty()) {
            Text(
                if (state.filter == StorylineFilter.CURRENT)
                    "Sin historias de la temporada actual con este filtro. Prueba \"Todas\"."
                else "Sin resultados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!state.hasAccount) {
            Text("Inicia sesión y sincroniza para ver tu progreso real en cada historia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 6.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { story ->
                Card(Modifier.fillMaxWidth().clickable { viewModel.open(story) }) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(story.name, style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f))
                            Text(
                                if (story.done) "✓ ${story.completed}/${story.total}"
                                else "${story.completed}/${story.total}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (story.done) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            story.zone?.let {
                                Text("📍 $it", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (story.campaign) {
                                Text("CAMPAÑA", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                        if (story.completed > 0 && !story.done) {
                            LinearProgressIndicator(
                                progress = { story.completed.toFloat() / story.total },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorylineDetail(state: StorylinesState, viewModel: StorylinesViewModel) {
    val story = state.selected!!
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = viewModel::back) { Text("← Historias") }
        Text(story.name, style = MaterialTheme.typography.headlineSmall)
        Text("${story.completed} / ${story.total} misiones completadas",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)
        if (state.detailLoading) {
            Center { CircularProgressIndicator() }
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp)) {
            items(state.selectedQuests, key = { it.id }) { q -> QuestRow(q) }
        }
    }
}

@Composable
private fun QuestRow(q: StorylineQuest) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (q.completed) "✓" else "○",
                style = MaterialTheme.typography.titleMedium,
                color = if (q.completed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f)) {
                Text(q.name, style = MaterialTheme.typography.bodyMedium)
                q.zone?.let {
                    Text("📍 $it", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                q.reward?.let {
                    Text("🎁 $it", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) { content() }
}
