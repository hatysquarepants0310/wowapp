package com.azeroth.companion.feature.quest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.azeroth.companion.R
import com.azeroth.companion.data.QuestFullDetail
import com.azeroth.companion.data.StorylinesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuestDetailViewModel @Inject constructor(
    private val repository: StorylinesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<QuestFullDetail?>(null)
    val state: StateFlow<QuestFullDetail?> = _state

    private var loaded = 0

    fun load(questId: Int) {
        if (questId == 0 || loaded == questId) return
        loaded = questId
        viewModelScope.launch { _state.value = repository.fullDetail(questId) }
    }
}

/**
 * Ficha de una misión: si la tienes hecha, dónde ocurre, qué da y el comando de
 * TomTom para llegar al punto de inicio. Es la misma pantalla para las semanales
 * y para las misiones de una historia, así que la información no cambia según
 * desde dónde llegues.
 */
@Composable
fun QuestDetailScreen(questId: Int, viewModel: QuestDetailViewModel = hiltViewModel()) {
    androidx.compose.runtime.LaunchedEffect(questId) { viewModel.load(questId) }
    val quest by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val detail = quest
    if (detail == null) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(detail.name, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (detail.completed) stringResource(R.string.quest_done)
                else stringResource(R.string.quest_not_done),
                style = MaterialTheme.typography.labelLarge,
                color = if (detail.completed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            detail.zone?.let {
                Text("📍 $it", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (detail.minLevel > 0) {
                Text(stringResource(R.string.quest_min_level, detail.minLevel),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        detail.storyline?.let {
            Text(stringResource(R.string.quest_in_storyline, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Lo que el usuario más va a usar: copiar y pegar en el chat del juego.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.tomtom_title),
                    style = MaterialTheme.typography.titleSmall)
                val command = detail.tomTom
                if (command == null) {
                    Text(stringResource(R.string.tomtom_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        command,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    Button(onClick = { copy(context, command) }) {
                        Text(stringResource(R.string.tomtom_copy))
                    }
                    Text(stringResource(R.string.tomtom_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        detail.description?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (detail.rewards.isNotEmpty()) {
            Text(
                if (detail.rewards.size > 1) stringResource(R.string.quest_reward_choice)
                else stringResource(R.string.quest_reward),
                style = MaterialTheme.typography.titleSmall,
            )
            detail.rewards.forEach { reward ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (reward.iconUrl != null) {
                            AsyncImage(
                                model = reward.iconUrl,
                                contentDescription = reward.name,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        } else {
                            Text("🎁")
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(reward.name, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(reward.chanceExplanation,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Text(stringResource(R.string.quest_id, detail.id),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

private fun copy(context: Context, command: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("TomTom", command))
    Toast.makeText(context, context.getString(R.string.tomtom_copied), Toast.LENGTH_SHORT).show()
}
