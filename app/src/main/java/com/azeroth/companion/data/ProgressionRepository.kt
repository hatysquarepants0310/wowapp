package com.azeroth.companion.data

import com.azeroth.companion.core.catalog.CatalogRepository
import com.azeroth.companion.core.database.ProgressionDao
import com.azeroth.companion.core.database.ProgressionStateEntity
import com.azeroth.companion.core.database.SnapshotDao
import com.azeroth.companion.core.database.TaskStateDao
import com.azeroth.companion.core.model.GreatVaultProgress
import com.azeroth.companion.core.vault.VaultCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressionRepository @Inject constructor(
    private val progressionDao: ProgressionDao,
    private val snapshotDao: SnapshotDao,
    private val taskStateDao: TaskStateDao,
    private val catalogRepository: CatalogRepository,
    private val eventsRepository: EventsRepository,
    private val json: Json,
) {

    fun observe(characterId: Long): Flow<ProgressionStateEntity?> = progressionDao.observe(characterId)

    suspend fun getOrDefault(characterId: Long): ProgressionStateEntity =
        progressionDao.get(characterId) ?: ProgressionStateEntity(characterId = characterId)

    suspend fun update(state: ProgressionStateEntity) =
        progressionDao.upsert(state.copy(updatedAt = Instant.now()))

    fun preyProgress(state: ProgressionStateEntity): Map<String, Int> = runCatching {
        json.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), state.preyProgressJson)
    }.getOrDefault(emptyMap())

    suspend fun setPreyProgress(characterId: Long, zone: String, percent: Int) {
        val state = getOrDefault(characterId)
        val updated = preyProgress(state) + (zone to percent.coerceIn(0, 100))
        update(state.copy(
            preyProgressJson = json.encodeToString(
                MapSerializer(String.serializer(), Int.serializer()), updated,
            ),
        ))
    }

    /**
     * Gran Bóveda estimada (§7.3): M+ del snapshot, raid por delta semanal de
     * kills, mundo por tareas contribuyentes completadas (catálogo decide cuáles).
     */
    suspend fun computeVault(characterId: Long): GreatVaultProgress {
        val catalog = catalogRepository.load()
        val now = Instant.now()
        val lastReset = eventsRepository.resetClock().lastWeeklyReset(now)

        val baseline = snapshotDao.firstAfter(characterId, lastReset)
        val current = snapshotDao.latest(characterId)

        val mplus = current?.mythicPlusRunsThisWeek ?: 0
        val raidKills = (sumKills(current?.raidKillsJson) - sumKills(baseline?.raidKillsJson))
            .coerceAtLeast(0)

        val states = taskStateDao.observeForCharacter(characterId).first()
        val world = states
            .filter { it.taskId in catalog.vault.worldContributingTaskIds }
            .filter { !it.periodStart.isBefore(lastReset) }
            .sumOf { it.completions }

        return VaultCalculator.vault(characterId, raidKills, mplus, world, catalog.vault)
    }

    private fun sumKills(raw: String?): Int = runCatching {
        raw?.let {
            json.decodeFromString(MapSerializer(Int.serializer(), Int.serializer()), it).values.sum()
        } ?: 0
    }.getOrDefault(0)
}
