package com.azeroth.companion.data

import com.azeroth.companion.core.database.SnapshotDao
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.network.BlizzardApiFactory
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class QuestZone(val id: Int, val name: String)
data class QuestEntry(val id: Int, val name: String, val completed: Boolean)
data class ZoneQuests(
    val zoneName: String,
    val quests: List<QuestEntry>,
    val completedCount: Int,
    val total: Int,
    val hasAccountData: Boolean,
)

/**
 * Rastreador de misiones por zona (§6, ampliado). Combina dos fuentes OFICIALES:
 * - quest/area: catálogo de misiones por zona (id + nombre).
 * - quests/completed del personaje (ya sincronizado): qué has completado.
 * El cruce marca cada misión ✓/○ automáticamente, como hace Wowhead.
 */
@Singleton
class QuestTrackerRepository @Inject constructor(
    private val apiFactory: BlizzardApiFactory,
    private val settingsRepository: SettingsRepository,
    private val snapshotDao: SnapshotDao,
    private val activeCharacter: ActiveCharacter,
    private val json: Json,
) {
    private var zonesCache: List<QuestZone>? = null
    private val zoneQuestCache = mutableMapOf<Int, List<Pair<Int, String>>>()

    suspend fun zones(): List<QuestZone> {
        zonesCache?.let { return it }
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        return runCatching {
            api.questAreaIndex(region.namespaceStatic).areas
                .filter { !it.name.isNullOrBlank() }
                .map { QuestZone(it.id, it.name!!) }
                .sortedBy { it.name }
                .also { zonesCache = it }
        }.getOrDefault(emptyList())
    }

    /** IDs de misiones completadas del personaje activo (del último snapshot). */
    private suspend fun completedQuestIds(): Set<Int> {
        val character = activeCharacter.current() ?: return emptySet()
        val snapshot = snapshotDao.latest(character.id) ?: return emptySet()
        return runCatching {
            json.decodeFromString(ListSerializer(Int.serializer()), snapshot.completedQuestIdsJson).toSet()
        }.getOrDefault(emptySet())
    }

    suspend fun zoneQuests(zoneId: Int, zoneName: String): ZoneQuests {
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        val completed = completedQuestIds()

        val quests = zoneQuestCache.getOrPut(zoneId) {
            runCatching {
                api.questArea(zoneId, region.namespaceStatic).quests
                    .filter { !it.name.isNullOrBlank() }
                    .map { it.id to it.name!! }
                    .sortedBy { it.second }
            }.getOrDefault(emptyList())
        }

        val entries = quests.map { (id, name) -> QuestEntry(id, name, id in completed) }
        return ZoneQuests(
            zoneName = zoneName,
            quests = entries,
            completedCount = entries.count { it.completed },
            total = entries.size,
            hasAccountData = completed.isNotEmpty(),
        )
    }
}
