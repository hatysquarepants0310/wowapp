package com.azeroth.companion.data

import com.azeroth.companion.core.catalog.CatalogRepository
import com.azeroth.companion.core.database.TaskStateDao
import com.azeroth.companion.core.database.TaskStateEntity
import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.ResetPeriod
import com.azeroth.companion.core.model.TaskState
import com.azeroth.companion.core.model.TrackedTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class TaskWithState(val task: TrackedTask, val state: TaskState?)

@Singleton
class WeeklyRepository @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val taskStateDao: TaskStateDao,
    private val eventsRepository: EventsRepository,
) {

    suspend fun tasks(includeLegacy: Boolean): List<TrackedTask> =
        catalogRepository.load().weeklyTasks.filter { includeLegacy || it.legacyExpansion == null }

    /**
     * Estados de la semana en curso. Un estado guardado antes del último reset
     * se trata como inexistente (equivale al "vuelve a 0" automático, criterio §13.6),
     * salvo las tareas ONE_TIME, que persisten entre semanas.
     */
    fun observeStates(characterId: Long, tasks: List<TrackedTask>, lastReset: Instant): Flow<List<TaskWithState>> =
        taskStateDao.observeForCharacter(characterId).map { entities ->
            val byId = entities.associateBy { it.taskId }
            tasks.map { task ->
                val entity = byId[task.id]
                val valid = entity != null &&
                    (task.resetPeriod == ResetPeriod.ONE_TIME || !entity.periodStart.isBefore(lastReset))
                TaskWithState(
                    task = task,
                    state = if (valid && entity != null) TaskState(
                        taskId = entity.taskId,
                        characterId = entity.characterId,
                        completions = entity.completions,
                        confidence = Confidence.valueOf(entity.confidence),
                        manualOverride = entity.manualOverride,
                        updatedAt = entity.updatedAt,
                    ) else null,
                )
            }
        }

    /** Override manual (§6): siempre disponible y gana sobre la inferencia. */
    suspend fun setManualCompletions(characterId: Long, task: TrackedTask, completions: Int) {
        val clock = eventsRepository.resetClock()
        val now = Instant.now()
        taskStateDao.upsert(
            TaskStateEntity(
                taskId = task.id,
                characterId = characterId,
                completions = completions.coerceIn(0, task.maxCompletions),
                confidence = Confidence.CONFIRMED.name,
                manualOverride = true,
                updatedAt = now,
                periodStart = if (task.resetPeriod == ResetPeriod.ONE_TIME) Instant.EPOCH
                else clock.lastWeeklyReset(now),
            ),
        )
    }

    /** Escribe un resultado inferido, sin pisar overrides manuales de la semana. */
    suspend fun setDetectedCompletions(characterId: Long, task: TrackedTask, completions: Int, confidence: Confidence) {
        val existing = taskStateDao.get(characterId, task.id)
        val clock = eventsRepository.resetClock()
        val now = Instant.now()
        val periodStart = clock.lastWeeklyReset(now)
        if (existing != null && existing.manualOverride && !existing.periodStart.isBefore(periodStart)) return
        taskStateDao.upsert(
            TaskStateEntity(
                taskId = task.id,
                characterId = characterId,
                completions = completions.coerceIn(0, task.maxCompletions),
                confidence = confidence.name,
                manualOverride = false,
                updatedAt = now,
                periodStart = periodStart,
            ),
        )
    }
}
