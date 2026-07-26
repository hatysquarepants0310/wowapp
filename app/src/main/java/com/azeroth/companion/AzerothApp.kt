package com.azeroth.companion

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.network.AuthManager
import com.azeroth.companion.core.notifications.NotificationChannels
import com.azeroth.companion.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AzerothApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.register(this)
        SyncScheduler.scheduleAll(this)
        // Restaurar y ALARGAR la sesión en cada arranque. Los tokens de usuario de
        // Blizzard viven 24 h y su grant de refresh no está habilitado para los
        // clientes de desarrollador; el grant token_extension sí, y lleva el token
        // a ~90 días. Hacerlo aquí (y no solo al abrir Ajustes) es lo que evita
        // tener que reconectar la cuenta a diario.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { authManager.restore(settingsRepository.settings.first().region) }
        }
    }
}
