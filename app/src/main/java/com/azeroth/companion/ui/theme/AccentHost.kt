package com.azeroth.companion.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.azeroth.companion.core.database.CharacterDao
import com.azeroth.companion.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Averigua de qué clase es el personaje activo para teñir con ella toda la app.
 *
 * Es lo que hace que la interfaz sea TUYA sin pedirte que elijas un tema: si
 * juegas un druida la app es naranja, si juegas un brujo es morada, y cambia
 * sola al cambiar de personaje activo.
 */
@HiltViewModel
class AccentViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val characterDao: CharacterDao,
) : ViewModel() {

    private val _className = MutableStateFlow<String?>(null)
    val className: StateFlow<String?> = _className

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                characterDao.observeAll(),
            ) { settings, roster ->
                val active = roster.firstOrNull { it.id == settings.activeCharacterId }
                    ?: roster.firstOrNull()
                active?.playableClass
            }.collect { _className.value = it }
        }
    }
}

/** Aplica el tema con el color de clase del personaje activo. */
@Composable
fun AzerothThemeForActiveCharacter(
    viewModel: AccentViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val className by viewModel.className.collectAsStateWithLifecycle()
    AzerothTheme(accent = ClassColors.forClassName(className), content = content)
}
