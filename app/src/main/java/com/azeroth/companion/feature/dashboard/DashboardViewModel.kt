package com.azeroth.companion.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.model.AuthState
import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.TrackedTask
import com.azeroth.companion.core.network.AuthManager
import com.azeroth.companion.data.EventsRepository
import com.azeroth.companion.data.WeeklyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DashboardState(
    val loading: Boolean = true,
    val nextEventId: String? = null,
    val nextEventName: String = "",
    val nextEventZone: String = "",
    val nextEventStartsAt: Instant? = null,
    val nextEventConfidence: Confidence = Confidence.PREDICTED,
    val weeklyResetAt: Instant? = null,
    val topPending: List<TrackedTask> = emptyList(),
    val authBroken: Boolean = false,
    val activeCharacterName: String? = null,
    val activeCharacterRealm: String? = null,
    val activeCharacterIlvl: Int = 0,
    val activeCharacterClass: String? = null,
    val lastSyncedAt: Instant? = null,
    val vault: com.azeroth.companion.core.model.GreatVaultProgress? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val eventsRepository: EventsRepository,
    private val weeklyRepository: WeeklyRepository,
    private val authManager: AuthManager,
    private val characterDao: com.azeroth.companion.core.database.CharacterDao,
    private val progressionRepository: com.azeroth.companion.data.ProgressionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    init {
        refresh()
        observeActiveCharacter()
    }

    /**
     * El personaje mostrado en Inicio es SIEMPRE el activo seleccionado en la
     * pantalla Personaje (settings.activeCharacterId), no el primero del roster.
     * Reactivo: al cambiar de personaje activo, Inicio se actualiza solo.
     */
    private fun observeActiveCharacter() {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                characterDao.observeAll(),
            ) { settings, roster ->
                roster.firstOrNull { it.id == settings.activeCharacterId }
                    ?: roster.firstOrNull()
            }.collect { active ->
                val vault = runCatching {
                    progressionRepository.computeVault(active?.id ?: 0L)
                }.getOrNull()
                _state.value = _state.value.copy(
                    activeCharacterName = active?.name,
                    activeCharacterRealm = active?.realmName,
                    activeCharacterIlvl = active?.equippedItemLevel ?: 0,
                    activeCharacterClass = active?.playableClass,
                    lastSyncedAt = active?.lastSyncedAt,
                    vault = vault,
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                eventsRepository.refreshCalibrations()
                val now = Instant.now()
                val next = eventsRepository.nextOccurrence(now)
                val reset = eventsRepository.resetClock().nextWeeklyReset(now)
                val pending = weeklyRepository.tasks(includeLegacy = false)
                    .sortedByDescending { it.priorityWeight }
                    .take(5)
                eventsRepository.rescheduleEventAlarms()
                _state.value = _state.value.copy(
                    loading = false,
                    nextEventId = next?.first?.id,
                    nextEventName = next?.first?.name?.get("es_MX")
                        ?: next?.first?.name?.values?.firstOrNull().orEmpty(),
                    nextEventZone = next?.first?.zone.orEmpty(),
                    nextEventStartsAt = next?.second?.startsAt,
                    nextEventConfidence = next?.second?.confidence ?: Confidence.PREDICTED,
                    weeklyResetAt = reset,
                    topPending = pending,
                    authBroken = authManager.state.value is AuthState.Broken,
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }
}
