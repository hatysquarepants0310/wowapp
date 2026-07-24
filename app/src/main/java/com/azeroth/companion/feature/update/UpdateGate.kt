package com.azeroth.companion.feature.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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

    fun install(apkUrl: String) {
        viewModelScope.launch {
            val result = updateChecker.downloadAndInstall(apkUrl)
            if (result is UpdateStatus.Error) _status.value = result else _dismissed.value = true
        }
    }

    fun later() { _dismissed.value = true }
}

/**
 * Pop-up al abrir la app: si hay una versión nueva en GitHub, ofrece instalarla
 * ahora o después. Comprobación silenciosa; no molesta si ya estás al día.
 */
@Composable
fun UpdateGate(viewModel: UpdateGateViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val dismissed by viewModel.dismissed.collectAsStateWithLifecycle()
    val available = status as? UpdateStatus.Available ?: return
    if (dismissed) return

    AlertDialog(
        onDismissRequest = viewModel::later,
        title = { Text("Actualización disponible") },
        text = {
            Text(
                "Hay una nueva versión (v${available.version}) de Azeroth Companion. " +
                    "¿Quieres instalarla ahora?",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = { viewModel.install(available.apkUrl) }) {
                Text("Instalar ahora")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::later) { Text("Después") }
        },
    )
}
