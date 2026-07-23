package com.azeroth.companion.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.azeroth.companion.core.datastore.Settings
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.model.Region
import com.azeroth.companion.core.notifications.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val settings: Settings? = null,
    val catalogVersion: Int? = null,
    val catalogSource: String = "embedded",
    val exactAlarms: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val catalogRepository: CatalogRepository,
    private val alarmScheduler: AlarmScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init {
        viewModelScope.launch {
            val catalog = catalogRepository.load()
            settingsRepository.settings.collect {
                _state.value = SettingsState(
                    settings = it,
                    catalogVersion = catalog.catalogVersion,
                    catalogSource = catalogRepository.activeSource,
                    exactAlarms = alarmScheduler.isExact,
                )
            }
        }
    }

    fun setRegion(region: Region) = viewModelScope.launch { settingsRepository.setRegion(region) }
    fun setShowLegacy(show: Boolean) = viewModelScope.launch { settingsRepository.setShowLegacy(show) }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings ?: return

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Región", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Region.entries.forEach { region ->
                FilterChip(
                    selected = settings.region == region,
                    onClick = { viewModel.setRegion(region) },
                    label = { Text(region.name) },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Mostrar contenido legacy")
            Switch(checked = settings.showLegacyContent, onCheckedChange = viewModel::setShowLegacy)
        }

        if (!state.exactAlarms) {
            Text(
                stringResource(R.string.exact_alarm_rationale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider()

        Text("Diagnóstico", style = MaterialTheme.typography.titleMedium)
        Text("Catálogo: v${state.catalogVersion} (${state.catalogSource})",
            style = MaterialTheme.typography.bodySmall)
        Text("Alarmas exactas: ${if (state.exactAlarms) "sí" else "no — modo ventana"}",
            style = MaterialTheme.typography.bodySmall)
        Text(
            "Avisos: ${settings.prewarnLongMinutes} min y ${settings.prewarnShortMinutes} min antes de cada evento; reset con ${settings.resetWarnHours} h de antelación.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()

        Text(
            stringResource(R.string.blizzard_trademark),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
