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
import com.azeroth.companion.core.catalog.CatalogRepository
import com.azeroth.companion.data.EventsRepository
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

object SyncScheduler {
    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

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
    }
}
