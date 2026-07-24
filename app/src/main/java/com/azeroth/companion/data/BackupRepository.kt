package com.azeroth.companion.data

import com.azeroth.companion.core.database.AppDatabase
import com.azeroth.companion.core.database.CalibrationObservationEntity
import com.azeroth.companion.core.database.ProgressionStateEntity
import com.azeroth.companion.core.database.SeasonalGoalEntity
import com.azeroth.companion.core.database.TaskStateEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exportación/importación de datos del usuario a JSON (§12): todos los datos
 * viven en el dispositivo y son portables. Nunca se suben a ningún servidor.
 */
@Serializable
data class BackupPayload(
    val formatVersion: Int = 1,
    val exportedAt: String,
    val taskStates: List<BackupTaskState> = emptyList(),
    val calibrations: List<BackupCalibration> = emptyList(),
    val progression: List<BackupProgression> = emptyList(),
    val seasonalGoals: List<BackupGoal> = emptyList(),
)

@Serializable
data class BackupTaskState(
    val taskId: String, val characterId: Long, val completions: Int,
    val confidence: String, val manualOverride: Boolean,
    val updatedAtEpochMs: Long, val periodStartEpochMs: Long,
)

@Serializable
data class BackupCalibration(val eventId: String, val observedAtEpochMs: Long)

@Serializable
data class BackupProgression(
    val characterId: Long, val folioUnlockedRows: Int, val folioCatchUpPending: Int,
    val preyProgressJson: String, val delveKeysAvailable: Int, val delvesDoneThisWeek: Int,
    val crestsThisWeek: Int, val crestsTotal: Int,
)

@Serializable
data class BackupGoal(val rewardId: String, val targeted: Boolean, val obtained: Boolean)

@Singleton
class BackupRepository @Inject constructor(
    private val db: AppDatabase,
    private val catalogRepository: com.azeroth.companion.core.catalog.CatalogRepository,
    private val json: Json,
) {

    suspend fun exportJson(): String {
        val characters = db.characterDao().observeAll().first()
        val taskStates = characters.flatMap { c ->
            db.taskStateDao().observeForCharacter(c.id).first()
        } + db.taskStateDao().observeForCharacter(0L).first() // perfil local sin sesión

        // Observaciones de calibración (§4.4): exportables, nunca obligatorio subirlas.
        val calibrations = catalogRepository.load().worldEvents.flatMap { def ->
            db.calibrationDao().latestFor(def.id)
        }
        val progression = characters.mapNotNull { db.progressionDao().get(it.id) } +
            listOfNotNull(db.progressionDao().get(0L))
        val goals = db.seasonalGoalDao().observeAll().first()

        return json.encodeToString(
            BackupPayload.serializer(),
            BackupPayload(
                exportedAt = Instant.now().toString(),
                taskStates = taskStates.distinct().map {
                    BackupTaskState(
                        it.taskId, it.characterId, it.completions, it.confidence,
                        it.manualOverride, it.updatedAt.toEpochMilli(), it.periodStart.toEpochMilli(),
                    )
                },
                calibrations = calibrations.map {
                    BackupCalibration(it.eventId, it.observedAt.toEpochMilli())
                },
                progression = progression.distinctBy { it.characterId }.map {
                    BackupProgression(
                        it.characterId, it.folioUnlockedRows, it.folioCatchUpPending,
                        it.preyProgressJson, it.delveKeysAvailable, it.delvesDoneThisWeek,
                        it.crestsThisWeek, it.crestsTotal,
                    )
                },
                seasonalGoals = goals.map { BackupGoal(it.rewardId, it.targeted, it.obtained) },
            ),
        )
    }

    /** Importa un backup; los datos importados hacen upsert sobre los locales. */
    suspend fun importJson(raw: String): Boolean {
        val payload = runCatching {
            json.decodeFromString(BackupPayload.serializer(), raw)
        }.getOrNull() ?: return false

        payload.taskStates.forEach {
            db.taskStateDao().upsert(
                TaskStateEntity(
                    taskId = it.taskId, characterId = it.characterId,
                    completions = it.completions, confidence = it.confidence,
                    manualOverride = it.manualOverride,
                    updatedAt = Instant.ofEpochMilli(it.updatedAtEpochMs),
                    periodStart = Instant.ofEpochMilli(it.periodStartEpochMs),
                ),
            )
        }
        payload.calibrations.forEach {
            db.calibrationDao().insert(
                CalibrationObservationEntity(
                    eventId = it.eventId,
                    observedAt = Instant.ofEpochMilli(it.observedAtEpochMs),
                ),
            )
        }
        payload.progression.forEach {
            db.progressionDao().upsert(
                ProgressionStateEntity(
                    characterId = it.characterId,
                    folioUnlockedRows = it.folioUnlockedRows,
                    folioCatchUpPending = it.folioCatchUpPending,
                    preyProgressJson = it.preyProgressJson,
                    delveKeysAvailable = it.delveKeysAvailable,
                    delvesDoneThisWeek = it.delvesDoneThisWeek,
                    crestsThisWeek = it.crestsThisWeek,
                    crestsTotal = it.crestsTotal,
                    updatedAt = Instant.now(),
                ),
            )
        }
        payload.seasonalGoals.forEach {
            db.seasonalGoalDao().upsert(
                SeasonalGoalEntity(
                    rewardId = it.rewardId, targeted = it.targeted,
                    obtained = it.obtained, updatedAt = Instant.now(),
                ),
            )
        }
        return true
    }
}
