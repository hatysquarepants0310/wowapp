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
data class StorylinesFile(
    val count: Int = 0,
    val storylines: List<StorylineDef> = emptyList(),
    val expansions: Map<String, String> = emptyMap(),
    val currentExpansion: Int = 11,
)

@Serializable
data class StorylineDef(
    val id: Int,
    val name: String,
    val questIds: List<Int>,
    val zone: String? = null,
    val campaign: Boolean = false,
    val exp: Int? = null,
)

/** Nivel 1 de la jerarquía: una expansión/temporada. */
data class SeasonNode(
    val expansionId: Int,
    val name: String,
    val isCurrent: Boolean,
    val storylineCount: Int,
    val completed: Int,
    val total: Int,
)

/** Nivel 2: categoría dentro de la temporada. */
enum class StoryCategory(val label: String) {
    CAMPAIGN("Campaña principal"),
    ZONE("Historias de zona"),
    OTHER("Otras historias"),
}

data class CategoryNode(
    val category: StoryCategory,
    val storylines: List<StorylineProgress>,
)

data class StorylineProgress(
    val id: Int,
    val name: String,
    val completed: Int,
    val total: Int,
    val done: Boolean,
    val zone: String?,
    val campaign: Boolean,
    val currentExpansion: Boolean,
    /** Posición sugerida dentro de su categoría (orden de realización). */
    val order: Int = 0,
)

data class StorylineQuest(
    val id: Int,
    val name: String,
    val completed: Boolean,
    val zone: String?,
    val reward: String?,
    val minLevel: Int = 0,
    val description: String? = null,
    /** Posición en la cadena: el orden en que se hace. */
    val step: Int = 0,
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
    private var fileCache: StorylinesFile? = null
    private val questDetailCache = mutableMapOf<Int, StorylineQuest>()

    private suspend fun file(): StorylinesFile = withContext(Dispatchers.IO) {
        fileCache?.let { return@withContext it }
        runCatching {
            val raw = context.assets.open("catalog/storylines.json").bufferedReader().readText()
            json.decodeFromString(StorylinesFile.serializer(), raw)
        }.getOrDefault(StorylinesFile()).also { fileCache = it }
    }

    private suspend fun defs(): List<StorylineDef> = file().storylines

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

    /**
     * Zonas de la expansión actual (Midnight). Permite priorizar "la temporada
     * actual primero", como pide el diseño. Vive aquí y no hardcodeado en la UI.
     */
    private val currentExpansionZones = setOf(
        "bosques de canción eterna", "eversong woods", "zul'aman", "harandar",
        "voidstorm", "isla de quel'danas", "quel'danas", "ciudad solaz",
        "silvermoon", "lunargenta", "isla enroscada", "coiled isle",
    )

    /** Nivel 1: temporadas/expansiones con progreso agregado, la actual primero. */
    suspend fun seasons(): Pair<Boolean, List<SeasonNode>> {
        val f = file()
        val completed = completedQuestIds()
        val byExp = f.storylines.filter { it.exp != null }.groupBy { it.exp!! }
        val nodes = byExp.map { (expId, list) ->
            val total = list.sumOf { it.questIds.size }
            val done = list.sumOf { def -> def.questIds.count { it in completed } }
            SeasonNode(
                expansionId = expId,
                name = f.expansions[expId.toString()] ?: "Expansión $expId",
                isCurrent = expId == f.currentExpansion,
                storylineCount = list.size,
                completed = done,
                total = total,
            )
        }.sortedWith(
            compareByDescending<SeasonNode> { it.isCurrent }.thenByDescending { it.expansionId },
        )
        return completed.isNotEmpty() to nodes
    }

    /** Nivel 2: categorías de una temporada, con sus historias numeradas en orden. */
    suspend fun categories(expansionId: Int): List<CategoryNode> {
        val completed = completedQuestIds()
        val currentExp = file().currentExpansion
        val list = defs().filter { it.exp == expansionId }
        fun toProgress(def: StorylineDef, order: Int): StorylineProgress {
            val done = def.questIds.count { it in completed }
            return StorylineProgress(
                id = def.id, name = def.name, completed = done, total = def.questIds.size,
                done = def.questIds.isNotEmpty() && done == def.questIds.size,
                zone = def.zone, campaign = def.campaign,
                currentExpansion = def.exp == currentExp, order = order,
            )
        }
        // El ID de QuestLine es aproximadamente cronológico: sirve como orden
        // sugerido de realización dentro de cada categoría.
        val campaign = list.filter { it.campaign }.sortedBy { it.id }
        val zoned = list.filter { !it.campaign && it.zone != null }
            .sortedWith(compareBy({ it.zone!!.lowercase() }, { it.id }))
        val other = list.filter { !it.campaign && it.zone == null }.sortedBy { it.id }
        return listOf(
            CategoryNode(StoryCategory.CAMPAIGN, campaign.mapIndexed { i, d -> toProgress(d, i + 1) }),
            CategoryNode(StoryCategory.ZONE, zoned.mapIndexed { i, d -> toProgress(d, i + 1) }),
            CategoryNode(StoryCategory.OTHER, other.mapIndexed { i, d -> toProgress(d, i + 1) }),
        ).filter { it.storylines.isNotEmpty() }
    }

    suspend fun storylines(): Pair<Boolean, List<StorylineProgress>> {
        val completed = completedQuestIds()
        val list = defs().map { def ->
            val done = def.questIds.count { it in completed }
            StorylineProgress(
                id = def.id, name = def.name,
                completed = done, total = def.questIds.size,
                done = def.questIds.isNotEmpty() && done == def.questIds.size,
                zone = def.zone,
                campaign = def.campaign,
                currentExpansion = def.zone?.lowercase()?.let { z ->
                    currentExpansionZones.any { z.contains(it) }
                } ?: false,
            )
        }.sortedWith(
            // Expansión actual primero, luego campañas, luego alfabético.
            compareByDescending<StorylineProgress> { it.currentExpansion }
                .thenByDescending { it.campaign }
                .thenBy { it.name.lowercase() },
        )
        return completed.isNotEmpty() to list
    }

    /** Detalle de una storyline: nombre, zona y recompensa de cada misión (API oficial). */
    suspend fun storylineQuests(id: Int): List<StorylineQuest> {
        val def = defs().firstOrNull { it.id == id } ?: return emptyList()
        val completed = completedQuestIds()
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        return def.questIds.mapIndexed { index, qid ->
            val step = index + 1
            questDetailCache[qid]?.copy(completed = qid in completed, step = step) ?: run {
                val detail = runCatching { api.quest(qid, region.namespaceStatic) }.getOrNull()
                val q = StorylineQuest(
                    id = qid,
                    name = detail?.title ?: "Misión #$qid",
                    completed = qid in completed,
                    zone = detail?.area?.name,
                    reward = detail?.rewards?.items?.items?.mapNotNull { it.item?.name }
                        ?.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    minLevel = detail?.requirements?.min_character_level ?: 0,
                    description = detail?.description?.takeIf { it.isNotBlank() },
                    step = step,
                )
                questDetailCache[qid] = q
                q
            }
        }
    }
}
