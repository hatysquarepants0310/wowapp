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
    private val updateChecker: com.azeroth.companion.core.update.UpdateChecker,
    private val syncRepository: com.azeroth.companion.data.SyncRepository,
) : ViewModel() {

    /** Mensaje visible del resultado del último sync manual. */
    private val _syncMessage = MutableStateFlow<Pair<Boolean, String>?>(null)
    val syncMessage: StateFlow<Pair<Boolean, String>?> = _syncMessage

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    fun syncNow() {
        viewModelScope.launch {
            _syncing.value = true
            _syncMessage.value = null
            val roster = syncRepository.syncRoster()
            val character = syncRepository.syncActiveCharacter()
            _syncing.value = false
            _syncMessage.value = when {
                character is com.azeroth.companion.data.SyncResult.Success ->
                    true to "Sincronizado con éxito. Datos de tu personaje actualizados."
                roster is com.azeroth.companion.data.SyncResult.Success &&
                    character is com.azeroth.companion.data.SyncResult.Failed ->
                    false to "Roster actualizado, pero el personaje falló: ${character.reason}"
                roster is com.azeroth.companion.data.SyncResult.Failed ->
                    false to roster.reason
                else -> false to "No se pudo sincronizar. Revisa tu sesión y conexión."
            }
        }
    }

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state
    val authState = authManager.state

    private val _updateStatus =
        MutableStateFlow<com.azeroth.companion.core.update.UpdateStatus?>(null)
    val updateStatus: StateFlow<com.azeroth.companion.core.update.UpdateStatus?> = _updateStatus

    val isAuthConfigured: Boolean get() = authManager.isConfigured

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateStatus.value = com.azeroth.companion.core.update.UpdateStatus.Checking
            _updateStatus.value = updateChecker.check()
        }
    }

    fun downloadUpdate(apkUrl: String, version: String) {
        viewModelScope.launch {
            _updateStatus.value =
                com.azeroth.companion.core.update.UpdateStatus.Downloading(version, 0)
            _updateStatus.value = updateChecker.downloadAndInstall(apkUrl, version) { pct ->
                _updateStatus.value =
                    com.azeroth.companion.core.update.UpdateStatus.Downloading(version, pct)
            }
        }
    }

    fun grantInstallPermission() = updateChecker.openInstallPermissionSettings()

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

    /**
     * Cambia el idioma de la app. Se guarda también en SharedPreferences porque
     * attachBaseContext lo necesita de forma síncrona en el arranque.
     */
    fun setLanguage(context: android.content.Context, tag: String?) {
        com.azeroth.companion.core.datastore.LanguagePref.write(context, tag)
        viewModelScope.launch { settingsRepository.setLanguage(tag) }
    }
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
                val syncing by viewModel.syncing.collectAsStateWithLifecycle()
                val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(
                        onClick = viewModel::syncNow,
                        enabled = !syncing,
                    ) { Text(if (syncing) "Sincronizando…" else "Sincronizar ahora") }
                    androidx.compose.material3.OutlinedButton(onClick = viewModel::logout) {
                        Text("Cerrar sesión")
                    }
                }
                syncMessage?.let { (ok, msg) ->
                    Text(
                        (if (ok) "✓ " else "⚠ ") + msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
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

        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val current = settings.language
            listOf(
                null to stringResource(R.string.language_system),
                "es" to "Español",
                "en" to "English",
            ).forEach { (tag, label) ->
                FilterChip(
                    selected = current == tag,
                    onClick = {
                        viewModel.setLanguage(context, tag)
                        // Recrear la actividad para que se apliquen los recursos.
                        (context as? android.app.Activity)?.recreate()
                    },
                    label = { Text(label) },
                )
            }
        }

        HorizontalDivider()

        Text(stringResource(R.string.settings_region), style = MaterialTheme.typography.titleMedium)
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

        Text("Actualización", style = MaterialTheme.typography.titleMedium)
        val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()
        Text(
            "App: v${com.azeroth.companion.BuildConfig.VERSION_NAME} " +
                "(build ${com.azeroth.companion.BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        when (val s = updateStatus) {
            is com.azeroth.companion.core.update.UpdateStatus.Checking ->
                Text("Buscando…", style = MaterialTheme.typography.bodySmall)
            is com.azeroth.companion.core.update.UpdateStatus.UpToDate ->
                Text("Ya tienes la última versión. ✓", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary)
            is com.azeroth.companion.core.update.UpdateStatus.Available -> {
                Text("¡Nueva versión v${s.version} disponible!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                androidx.compose.material3.Button(onClick = {
                    viewModel.downloadUpdate(s.apkUrl, s.version)
                }) { Text("Descargar e instalar v${s.version}") }
            }
            is com.azeroth.companion.core.update.UpdateStatus.Downloading ->
                Text("Descargando v${s.version}… ${s.percent}%",
                    style = MaterialTheme.typography.bodySmall)
            is com.azeroth.companion.core.update.UpdateStatus.NeedsInstallPermission -> {
                Text("Android necesita permiso para instalar la actualización.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                androidx.compose.material3.Button(onClick = viewModel::grantInstallPermission) {
                    Text("Abrir ajustes de permiso")
                }
            }
            is com.azeroth.companion.core.update.UpdateStatus.ReadyToInstall ->
                Text("Instalador abierto. Confirma la instalación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            is com.azeroth.companion.core.update.UpdateStatus.Error ->
                Text("⚠ ${s.reason}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            null -> {}
        }
        androidx.compose.material3.OutlinedButton(onClick = { viewModel.checkForUpdate() }) {
            Text("Buscar actualización")
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
