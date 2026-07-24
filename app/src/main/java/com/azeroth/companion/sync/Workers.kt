package com.azeroth.companion.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import com.azeroth.companion.core.catalog.CatalogRepository
import com.azeroth.companion.data.EventsRepository
import com.azeroth.companion.data.SyncRepository
import com.azeroth.companion.data.SyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Recalcula y reprograma todas las alarmas. Se ejecuta tras cada sync, en el
 * arranque del sistema y ante cambios de zona horaria (§5.3, §10).
 */
@HiltWorker
class RescheduleAlarmsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val eventsRepository: EventsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        eventsRepository.refreshCalibrations()
        eventsRepository.rescheduleEventAlarms()
        // Refresca el widget de pantalla de inicio con el próximo evento (§9.8).
        com.azeroth.companion.widget.NextEventWidget().updateAll(applicationContext)
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}

/**
 * Actualización opcional del catálogo remoto (§7). Sin URL configurada es un
 * no-op: la app funciona indefinidamente con el catálogo embebido.
 */
@HiltWorker
class CatalogUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val catalogRepository: CatalogRepository,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: DEFAULT_URL ?: return Result.success()
        return runCatching {
            okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return Result.retry()
                catalogRepository.storeDownloaded(response.body?.string().orEmpty())
            }
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        const val KEY_URL = "catalog_url"
        /** null = solo catálogo embebido. Configurable en un fork/despliegue propio. */
        val DEFAULT_URL: String? = null
    }
}

/** Sync del personaje activo cada 30 min con red disponible (§10). */
@HiltWorker
class SyncActiveCharacterWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val eventsRepository: EventsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (syncRepository.syncActiveCharacter()) {
        is SyncResult.Success -> {
            eventsRepository.rescheduleEventAlarms()
            Result.success()
        }
        is SyncResult.NotLoggedIn -> Result.success() // modo degradado: no es un error
        is SyncResult.Failed -> if (runAttemptCount < 4) Result.retry() else Result.success()
    }
}

/** Sync del roster completo cada 6 h, sin batería baja (§10). */
@HiltWorker
class SyncRosterWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (syncRepository.syncRoster()) {
        is SyncResult.Success, is SyncResult.NotLoggedIn -> Result.success()
        is SyncResult.Failed -> if (runAttemptCount < 4) Result.retry() else Result.success()
    }
}

object SyncScheduler {
    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val networkNotLowBattery = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        wm.enqueueUniquePeriodicWork(
            "reschedule_alarms",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RescheduleAlarmsWorker>(30, TimeUnit.MINUTES).build(),
        )
        wm.enqueueUniquePeriodicWork(
            "catalog_update",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CatalogUpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(network)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            "sync_active_character",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncActiveCharacterWorker>(30, TimeUnit.MINUTES)
                .setConstraints(network)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            "sync_roster",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncRosterWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkNotLowBattery)
                .build(),
        )
    }

    /**
     * Sync inmediato (login, apertura de la app, botón manual). Trabajo único
     * con KEEP: aperturas repetidas no acumulan peticiones (rate limit local, §10).
     */
    fun syncNow(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniqueWork(
            "sync_now_roster",
            androidx.work.ExistingWorkPolicy.KEEP,
            androidx.work.OneTimeWorkRequestBuilder<SyncRosterWorker>().build(),
        )
        wm.enqueueUniqueWork(
            "sync_now_character",
            androidx.work.ExistingWorkPolicy.KEEP,
            androidx.work.OneTimeWorkRequestBuilder<SyncActiveCharacterWorker>().build(),
        )
    }
}
