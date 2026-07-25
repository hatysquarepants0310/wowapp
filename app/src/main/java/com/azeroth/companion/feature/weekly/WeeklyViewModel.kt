package com.azeroth.companion.feature.weekly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.data.WeeklyActivity
import com.azeroth.companion.data.WeeklyActivityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeeklyState(
    val loading: Boolean = true,
    val activity: WeeklyActivity = WeeklyActivity(),
)

/**
 * "Esta semana" 100% automático. Antes esta pantalla pintaba una checklist de
 * tareas del catálogo que en su mayoría eran ManualOnly: sin entrada manual
 * (por diseño de la app) nunca se marcaban y siempre salía 0/N. Ahora enseña lo
 * que la API sí demuestra: cada M+, cada jefe, cada Delve y cada misión hecha
 * desde el reset.
 */
@HiltViewModel
class WeeklyViewModel @Inject constructor(
    private val weeklyActivityRepository: WeeklyActivityRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WeeklyState())
    val state: StateFlow<WeeklyState> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = WeeklyState(loading = false, activity = weeklyActivityRepository.load())
        }
    }
}
