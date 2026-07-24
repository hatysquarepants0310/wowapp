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
import com.azeroth.companion.core.model.AuthState
import com.azeroth.companion.core.model.Region
import com.azeroth.companion.core.network.AuthManager
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
    private val authManager: AuthManager,
    private val backupRepository: com.azeroth.companion.data.BackupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state
    val authState = authManager.state

    val isAuthConfigured: Boolean get() = authManager.isConfigured

    init {
        viewModelScope.launch { authManager.restore() }
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
    fun logout() = viewModelScope.launch { authManager.logout() }

    /** URL de autorización PKCE, o null si no hay client_id configurado. */
    suspend fun buildLoginUri(): android.net.Uri? {
        if (!authManager.isConfigured) return null
        val region = _state.value.settings?.region ?: Region.US
        return authManager.buildAuthorizationUri(region)
    }

    suspend fun exportJson(): String = backupRepository.exportJson()
    suspend fun importJson(raw: String): Boolean = backupRepository.importJson(raw)
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val settings = state.settings ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Cuenta de Battle.net", style = MaterialTheme.typography.titleMedium)
        when (val auth = authState) {
            is AuthState.LoggedIn -> {
                Text("Sesión iniciada${auth.battleTag?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(onClick = {
                        com.azeroth.companion.sync.SyncScheduler.syncNow(context)
                    }) { Text("Sincronizar ahora") }
                    androidx.compose.material3.OutlinedButton(onClick = viewModel::logout) {
                        Text("Cerrar sesión")
                    }
                }
            }
            is AuthState.Broken -> {
                Text("⚠ ${auth.reason}", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                LoginButton(viewModel, scope, context)
            }
            AuthState.LoggedOut -> {
                Text(
                    "Solo lectura, scope wow.profile. Tus datos nunca salen del dispositivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (viewModel.isAuthConfigured) {
                    LoginButton(viewModel, scope, context)
                } else {
                    Text(
                        "Compilación sin client_id de Blizzard: el login está deshabilitado. " +
                            "La app funciona completa en modo manual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        HorizontalDivider()

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
        Text(
            "App: v${com.azeroth.companion.BuildConfig.VERSION_NAME} " +
                "(build ${com.azeroth.companion.BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text("Catálogo: v${state.catalogVersion} (${state.catalogSource})",
            style = MaterialTheme.typography.bodySmall)
        Text("Alarmas exactas: ${if (state.exactAlarms) "sí" else "no — modo ventana"}",
            style = MaterialTheme.typography.bodySmall)
        Text(
            "Avisos: ${settings.prewarnLongMinutes} min y ${settings.prewarnShortMinutes} min antes de cada evento; reset con ${settings.resetWarnHours} h de antelación.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()

        Text("Tus datos", style = MaterialTheme.typography.titleMedium)
        val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            uri?.let {
                scope.launch {
                    val payload = viewModel.exportJson()
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        out.write(payload.toByteArray())
                    }
                }
            }
        }
        val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                scope.launch {
                    val raw = context.contentResolver.openInputStream(it)
                        ?.bufferedReader()?.readText()
                    if (raw != null) viewModel.importJson(raw)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = {
                exportLauncher.launch("azeroth-companion-backup.json")
            }) { Text("Exportar a JSON") }
            androidx.compose.material3.OutlinedButton(onClick = {
                importLauncher.launch(arrayOf("application/json"))
            }) { Text("Importar") }
        }
        Text(
            "Progreso, overrides, calibraciones y objetivos. Todo local, nada se sube a ningún servidor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        Text(
            stringResource(R.string.blizzard_trademark),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Abre el flujo OAuth en Custom Tab, nunca en WebView embebido (§2.1). */
@Composable
private fun LoginButton(
    viewModel: SettingsViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
) {
    androidx.compose.material3.Button(onClick = {
        scope.launch {
            viewModel.buildLoginUri()?.let { uri ->
                androidx.browser.customtabs.CustomTabsIntent.Builder().build()
                    .launchUrl(context, uri)
            }
        }
    }) { Text("Iniciar sesión con Battle.net") }
}
