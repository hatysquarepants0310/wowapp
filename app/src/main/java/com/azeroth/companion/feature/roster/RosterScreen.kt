package com.azeroth.companion.feature.roster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.database.CharacterDao
import com.azeroth.companion.core.database.CharacterEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RosterViewModel @Inject constructor(characterDao: CharacterDao) : ViewModel() {
    val characters = characterDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<CharacterEntity>())
}

@Composable
fun RosterScreen(viewModel: RosterViewModel = hiltViewModel()) {
    val characters by viewModel.characters.collectAsStateWithLifecycle()

    if (characters.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Sin personajes sincronizados", style = MaterialTheme.typography.titleMedium)
            Text(
                "Inicia sesión con Battle.net en Ajustes para importar tu roster. " +
                    "Los intentos de montura y los límites semanales son por personaje.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(characters, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${c.name} · ${c.realmName}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${c.playableClass}${c.activeSpec?.let { " · $it" } ?: ""} · ilvl ${c.equippedItemLevel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
