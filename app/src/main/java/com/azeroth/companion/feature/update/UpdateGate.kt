package com.azeroth.companion.feature.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.azeroth.companion.ui.components.WowButton
import com.azeroth.companion.ui.components.WowDialog
import com.azeroth.companion.ui.components.WowProgress
import com.azeroth.companion.ui.components.WowTextButton
import com.azeroth.companion.ui.theme.NumberStyle
import com.azeroth.companion.core.update.UpdateChecker
import com.azeroth.companion.core.update.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateGateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    private val _status = MutableStateFlow<UpdateStatus?>(null)
    val status: StateFlow<UpdateStatus?> = _status

    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed

    init {
        // Al abrir la app: comprobación silenciosa una vez.
        viewModelScope.launch { _status.value = updateChecker.check() }
    }

    fun install(apkUrl: String, version: String) {
        viewModelScope.launch {
            _status.value = UpdateStatus.Downloading(version, 0)
            val result = updateChecker.downloadAndInstall(apkUrl, version) { pct ->
                _status.value = UpdateStatus.Downloading(version, pct)
            }
            _status.value = result
            if (result is UpdateStatus.ReadyToInstall) _dismissed.value = true
        }
    }

    fun grantPermission() = updateChecker.openInstallPermissionSettings()

    fun later() { _dismissed.value = true }
}

/**
 * Pop-up al abrir la app: si hay una versión nueva en GitHub, ofrece instalarla
 * ahora o después, con progreso de descarga y ruta clara si falta el permiso de
 * "instalar apps desconocidas" (causa habitual del error de paquete).
 */
@Composable
fun UpdateGate(viewModel: UpdateGateViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val dismissed by viewModel.dismissed.collectAsStateWithLifecycle()
    if (dismissed) return

    when (val s = status) {
        is UpdateStatus.Available -> WowDialog(
            title = "Actualización disponible",
            onDismiss = viewModel::later,
            body = {
                Text(
                    "Hay una nueva versión (v${s.version}) de Azeroth Companion" +
                        (if (s.sizeBytes > 0) " · ${s.sizeBytes / 1_048_576} MB" else "") +
                        ". ¿Quieres instalarla ahora?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            actions = {
                WowButton(
                    "Instalar ahora",
                    onClick = { viewModel.install(s.apkUrl, s.version) },
                    primary = true,
                )
                WowTextButton("Después", onClick = viewModel::later)
            },
        )

        is UpdateStatus.Downloading -> WowDialog(
            title = "Descargando v${s.version}",
            onDismiss = {},
            // Sin salida: la descarga ya está en marcha y cerrar aquí dejaría el
            // archivo a medias sin forma de retomarlo.
            dismissible = false,
            body = {
                Column(Modifier.fillMaxWidth()) {
                    Text("${s.percent}%", style = NumberStyle)
                    WowProgress(
                        s.percent / 100f,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            actions = {},
        )

        is UpdateStatus.NeedsInstallPermission -> WowDialog(
            title = "Permiso necesario",
            onDismiss = viewModel::later,
            body = {
                Text(
                    "Android necesita tu permiso para instalar la actualización. " +
                        "Activa \"Permitir de esta fuente\" y vuelve a pulsar Instalar.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            actions = {
                WowButton("Abrir ajustes", onClick = { viewModel.grantPermission() }, primary = true)
                WowTextButton("Después", onClick = viewModel::later)
            },
        )

        is UpdateStatus.Error -> WowDialog(
            title = "No se pudo actualizar",
            onDismiss = viewModel::later,
            body = { Text(s.reason, style = MaterialTheme.typography.bodySmall) },
            actions = { WowTextButton("Cerrar", onClick = viewModel::later) },
        )

        else -> Unit
    }
}
