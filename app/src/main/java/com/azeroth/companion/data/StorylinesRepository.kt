package com.azeroth.companion.data

import android.content.Context
import com.azeroth.companion.core.database.CharacterDao
import com.azeroth.companion.core.database.SnapshotDao
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.network.BlizzardApiFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class StorylinesFile(val count: Int = 0, val storylines: List<StorylineDef> = emptyList())

@Serializable
data class StorylineDef(val id: Int, val name: String, val questIds: List<Int>)

data class StorylineProgress(
    val id: Int,
    val name: String,
    val completed: Int,
    val total: Int,
    val done: Boolean,
)

data class StorylineQuest(
    val id: Int,
    val name: String,
    val completed: Boolean,
    val zone: String?,
    val reward: String?,
)

/**
 * Storylines de WoW (estilo Wowhead) a partir de un dataset OFICIAL del cliente
 * del juego (tablas DB2 QuestLine/QuestLineXQuest exportadas de wago.tools por
 * nuestro pipeline y horneadas en assets). Cero scraping, cero dependencia en
 * runtime: la app solo lee el JSON local y lo cruza con tus misiones completadas.
 */
@Singleton
class StorylinesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiFactory: BlizzardApiFactory,
    private val settingsRepository: SettingsRepository,
    private val snapshotDao: SnapshotDao,
    private val characterDao: CharacterDao,
    private val json: Json,
) {
    private var defsCache: List<StorylineDef>? = null
    private val questDetailCache = mutableMapOf<Int, StorylineQuest>()

    private suspend fun defs(): List<StorylineDef> = withContext(Dispatchers.IO) {
        defsCache?.let { return@withContext it }
        runCatching {
            val raw = context.assets.open("catalog/storylines.json").bufferedReader().readText()
            json.decodeFromString(StorylinesFile.serializer(), raw).storylines
        }.getOrDefault(emptyList()).also { defsCache = it }
    }

    private suspend fun completedQuestIds(): Set<Int> {
        val settings = settingsRepository.settings.first()
        val roster = characterDao.observeAll().first()
        val character = roster.firstOrNull { it.id == settings.activeCharacterId }
            ?: roster.firstOrNull() ?: return emptySet()
        val snapshot = snapshotDao.latest(character.id) ?: return emptySet()
        return runCatching {
            json.decodeFromString(ListSerializer(Int.serializer()), snapshot.completedQuestIdsJson).toSet()
        }.getOrDefault(emptySet())
    }

    suspend fun storylines(): Pair<Boolean, List<StorylineProgress>> {
        val completed = completedQuestIds()
        val list = defs().map { def ->
            val done = def.questIds.count { it in completed }
            StorylineProgress(
                id = def.id, name = def.name,
                completed = done, total = def.questIds.size,
                done = def.questIds.isNotEmpty() && done == def.questIds.size,
            )
        }.sortedBy { it.name.lowercase() }
        return completed.isNotEmpty() to list
    }

    /** Detalle de una storyline: nombre, zona y recompensa de cada misión (API oficial). */
    suspend fun storylineQuests(id: Int): List<StorylineQuest> {
        val def = defs().firstOrNull { it.id == id } ?: return emptyList()
        val completed = completedQuestIds()
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        return def.questIds.map { qid ->
            questDetailCache[qid]?.copy(completed = qid in completed) ?: run {
                val detail = runCatching { api.quest(qid, region.namespaceStatic) }.getOrNull()
                val q = StorylineQuest(
                    id = qid,
                    name = detail?.title ?: "Misión #$qid",
                    completed = qid in completed,
                    zone = detail?.area?.name,
                    reward = detail?.rewards?.items?.items?.mapNotNull { it.item?.name }
                        ?.takeIf { it.isNotEmpty() }?.joinToString(", "),
                )
                questDetailCache[qid] = q
                q
            }
        }
    }
}
