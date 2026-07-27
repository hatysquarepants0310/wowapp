package com.azeroth.companion.data

import android.content.Context
import com.azeroth.companion.core.database.SnapshotDao
import com.azeroth.companion.core.datastore.LanguagePref
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.loot.DropChance
import com.azeroth.companion.core.loot.DropChanceCalculator
import com.azeroth.companion.core.network.BlizzardApiFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class StorylinesFile(
    val count: Int = 0,
    val currentExpansion: Int = 11,
    val expansions: Map<String, String> = emptyMap(),
    val campaigns: List<CampaignDef> = emptyList(),
    val storylines: List<StorylineDef> = emptyList(),
)

/**
 * Campaña tal como la define el juego (tablas Campaign/CampaignXQuestLine):
 * [lines] ya viene en el orden en que se juegan los capítulos.
 */
@Serializable
data class CampaignDef(
    val id: Int,
    val name: String,
    val nameEs: String? = null,
    val lines: List<Int> = emptyList(),
    val exp: Int? = null,
)

@Serializable
data class StorylineDef(
    val id: Int,
    val name: String,
    val nameEs: String? = null,
    val questIds: List<Int> = emptyList(),
    /** Misiones OPCIONALES de la cadena (QuestLineXQuest.Flags=1). */
    val opt: List<Int> = emptyList(),
    /** ID de zona (AreaTable) donde ocurre la historia. */
    val area: Int? = null,
    /** Campaña a la que pertenece, si pertenece a alguna. */
    val camp: Int? = null,
    val exp: Int? = null,
)

/** Nivel 1 de la jerarquía: una expansión/temporada. */
data class SeasonNode(
    val expansionId: Int,
    val name: String,
    val isCurrent: Boolean,
    val storylineCount: Int,
    /** Historias completas / historias totales de la temporada. */
    val completed: Int,
    val total: Int,
    val campaignCount: Int,
)

enum class CategoryKind { CAMPAIGN, ZONE, OTHER }

/**
 * Nivel 2: una campaña real del juego, o el conjunto de historias sueltas de una
 * zona. Antes eran tres cajones fijos ("Campaña principal"/"Historias de
 * zona"/"Otras") y el de campaña salía de buscar la palabra "campaign" en el
 * nombre, así que quedaba vacío y todo caía en el mismo saco numerado 1..N.
 */
data class CategoryNode(
    val key: String,
    val label: String,
    val kind: CategoryKind,
    val storylines: List<StorylineProgress>,
) {
    val completed: Int get() = storylines.count { it.done }
    val total: Int get() = storylines.size
}

data class StorylineProgress(
    val id: Int,
    val name: String,
    /** Misiones obligatorias hechas. */
    val completed: Int,
    /** Misiones obligatorias de la cadena. */
    val total: Int,
    val done: Boolean,
    val zone: String?,
    val campaign: String?,
    val currentExpansion: Boolean,
    /** Posición dentro de su categoría: el orden en que se juega. */
    val order: Int = 0,
    /** Misiones opcionales hechas y disponibles (no cuentan para completar). */
    val optionalCompleted: Int = 0,
    val optionalTotal: Int = 0,
)

/** Recompensa de una misión, con imagen y probabilidad. */
data class QuestReward(
    val itemId: Int,
    val name: String,
    val iconUrl: String?,
    val chancePercent: Double?,
    val chanceExplanation: String,
)

data class StorylineQuest(
    val id: Int,
    val name: String,
    val completed: Boolean,
    val zone: String?,
    val minLevel: Int = 0,
    val description: String? = null,
    /** Posición en la cadena: el orden en que se hace. */
    val step: Int = 0,
    /** Misión opcional: no hace falta para completar la historia. */
    val optional: Boolean = false,
    val rewardItems: List<QuestReward> = emptyList(),
)

/**
 * Historias de WoW a partir de un dataset OFICIAL del cliente del juego (tablas
 * DB2 QuestLine/QuestLineXQuest/Campaign/CampaignXQuestLine exportadas de
 * wago.tools) más los nombres y zonas de la API oficial de Blizzard, todo
 * horneado en assets por tools/build_storylines.py. Cero scraping y cero
 * dependencia en runtime: la app lee JSON local y lo cruza con tus misiones
 * completadas.
 */
@Singleton
class StorylinesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiFactory: BlizzardApiFactory,
    private val settingsRepository: SettingsRepository,
    private val snapshotDao: SnapshotDao,
    private val activeCharacter: ActiveCharacter,
    private val json: Json,
) {
    private var fileCache: StorylinesFile? = null
    private var questNames: Map<Int, String>? = null
    private var questMeta: Map<Int, List<Int>>? = null
    private var areaNames: Map<Int, String>? = null
    private var coords: Map<Int, List<Double>>? = null
    private val questDetailCache = mutableMapOf<Int, QuestDetail>()

    private fun spanish(): Boolean =
        (LanguagePref.read(context) ?: java.util.Locale.getDefault().language).startsWith("es")

    private suspend fun file(): StorylinesFile = withContext(Dispatchers.IO) {
        fileCache?.let { return@withContext it }
        readAsset("catalog/storylines.json") {
            json.decodeFromString(StorylinesFile.serializer(), it)
        }.also { fileCache = it } ?: StorylinesFile().also { fileCache = it }
    }

    private suspend fun names(): Map<Int, String> = withContext(Dispatchers.IO) {
        questNames?.let { return@withContext it }
        val asset = if (spanish()) "catalog/quests_es.json" else "catalog/quests_en.json"
        val parsed = readAsset(asset) {
            json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it)
                .mapKeys { (k, _) -> k.toInt() }
        }.orEmpty()
        parsed.also { questNames = it }
    }

    private suspend fun meta(): Map<Int, List<Int>> = withContext(Dispatchers.IO) {
        questMeta?.let { return@withContext it }
        readAsset("catalog/quest_meta.json") {
            json.decodeFromString(
                MapSerializer(String.serializer(), ListSerializer(Int.serializer())), it,
            ).mapKeys { (k, _) -> k.toInt() }
        }.orEmpty().also { questMeta = it }
    }

    private suspend fun areas(): Map<Int, String> = withContext(Dispatchers.IO) {
        areaNames?.let { return@withContext it }
        val asset = if (spanish()) "catalog/areas_es.json" else "catalog/areas_en.json"
        readAsset(asset) {
            json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it)
                .mapKeys { (k, _) -> k.toInt() }
        }.orEmpty().also { areaNames = it }
    }

    /**
     * Coordenada del punto de la misión: [uiMapId, x, y]. La genera
     * tools/build_quest_coords.py desde las tablas QuestPOI del cliente.
     */
    private suspend fun coords(): Map<Int, List<Double>> = withContext(Dispatchers.IO) {
        coords?.let { return@withContext it }
        readAsset("catalog/quest_coords.json") {
            json.decodeFromString(
                MapSerializer(String.serializer(), ListSerializer(Double.serializer())), it,
            ).mapKeys { (k, _) -> k.toInt() }
        }.orEmpty().also { coords = it }
    }

    /**
     * Comando de TomTom para llegar al punto de la misión, listo para pegar en
     * el chat del juego. Null si el cliente no publica un punto para esa misión.
     */
    suspend fun tomTomCommand(questId: Int, questName: String): String? {
        val c = coords()[questId] ?: return null
        val map = c.getOrNull(0)?.toInt() ?: return null
        val x = c.getOrNull(1) ?: return null
        val y = c.getOrNull(2) ?: return null
        return "/way #$map ${trim(x)} ${trim(y)} $questName"
    }

    private fun trim(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)

    private fun <T> readAsset(path: String, parse: (String) -> T): T? = runCatching {
        parse(context.assets.open(path).bufferedReader().use { it.readText() })
    }.getOrNull()

    private fun StorylineDef.label(spanish: Boolean) =
        if (spanish) nameEs?.takeIf { it.isNotBlank() } ?: name else name

    private fun CampaignDef.label(spanish: Boolean) =
        if (spanish) nameEs?.takeIf { it.isNotBlank() } ?: name else name

    /** Misiones que hay que hacer para completar la historia (sin las opcionales). */
    private fun StorylineDef.required(): List<Int> = questIds.filterNot { it in opt }

    private suspend fun completedQuestIds(): Set<Int> {
        val character = activeCharacter.current() ?: return emptySet()
        val snapshot = snapshotDao.latest(character.id) ?: return emptySet()
        return runCatching {
            json.decodeFromString(ListSerializer(Int.serializer()), snapshot.completedQuestIdsJson).toSet()
        }.getOrDefault(emptySet())
    }

    /** Nivel 1: temporadas/expansiones con progreso agregado, la actual primero. */
    suspend fun seasons(): Pair<Boolean, List<SeasonNode>> {
        val f = file()
        val completed = completedQuestIds()
        val byExp = f.storylines.filter { it.exp != null }.groupBy { it.exp!! }
        val campaignsByExp = f.campaigns.groupBy { it.exp }
        val nodes = byExp.map { (expId, list) ->
            // Se cuenta en HISTORIAS completas, no en misiones sueltas: es lo que
            // el jugador reconoce como progreso de la temporada.
            val done = list.count { def ->
                val req = def.required()
                req.isNotEmpty() && req.all { it in completed }
            }
            SeasonNode(
                expansionId = expId,
                name = f.expansions[expId.toString()] ?: "Expansión $expId",
                isCurrent = expId == f.currentExpansion,
                storylineCount = list.size,
                completed = done,
                total = list.size,
                campaignCount = campaignsByExp[expId]?.size ?: 0,
            )
        }.sortedWith(
            compareByDescending<SeasonNode> { it.isCurrent }.thenByDescending { it.expansionId },
        )
        return completed.isNotEmpty() to nodes
    }

    /**
     * Nivel 2: las campañas de la temporada (en su orden real) y, después, las
     * historias sueltas agrupadas por zona. La numeración es POR CATEGORÍA:
     * cada campaña numera sus capítulos 1..N con el orden del juego.
     */
    suspend fun categories(expansionId: Int): List<CategoryNode> {
        val f = file()
        val completed = completedQuestIds()
        val es = spanish()
        val areaNames = areas()
        val byId = f.storylines.associateBy { it.id }
        val inExpansion = f.storylines.filter { it.exp == expansionId }
        val campaignNames = f.campaigns.associate { it.id to it.label(es) }

        fun progress(def: StorylineDef, order: Int): StorylineProgress {
            val req = def.required()
            val done = req.count { it in completed }
            return StorylineProgress(
                id = def.id,
                name = def.label(es),
                completed = done,
                total = req.size,
                done = req.isNotEmpty() && done == req.size,
                zone = def.area?.let { areaNames[it] },
                campaign = def.camp?.let { campaignNames[it] },
                currentExpansion = expansionId == f.currentExpansion,
                order = order,
                optionalCompleted = def.opt.count { it in completed },
                optionalTotal = def.opt.size,
            )
        }

        // Una campaña pertenece a la temporada si su expansión coincide; sus
        // capítulos se listan aunque alguno se clasificara en otra expansión.
        val campaignNodes = f.campaigns
            .filter { it.exp == expansionId }
            .map { camp ->
                CategoryNode(
                    key = "camp:${camp.id}",
                    label = camp.label(es),
                    kind = CategoryKind.CAMPAIGN,
                    storylines = camp.lines.mapNotNull { byId[it] }
                        .mapIndexed { i, def -> progress(def, i + 1) },
                )
            }
            .filter { it.storylines.isNotEmpty() }
            .sortedByDescending { it.total }

        val inCampaign = campaignNodes.flatMap { it.storylines }.map { it.id }.toSet()
        val loose = inExpansion.filterNot { it.id in inCampaign }

        val zoneNodes = loose.filter { it.area != null }
            .groupBy { it.area!! }
            .map { (areaId, list) ->
                CategoryNode(
                    key = "zone:$areaId",
                    label = areaNames[areaId] ?: "Zona $areaId",
                    kind = CategoryKind.ZONE,
                    // Los IDs de QuestLine se emiten en orden cronológico: sirven
                    // como orden sugerido dentro de la zona.
                    storylines = list.sortedBy { it.id }.mapIndexed { i, d -> progress(d, i + 1) },
                )
            }
            .sortedBy { it.label.lowercase() }

        val otherNode = loose.filter { it.area == null }
            .sortedBy { it.id }
            .mapIndexed { i, d -> progress(d, i + 1) }
            .takeIf { it.isNotEmpty() }
            ?.let { CategoryNode("other", "Otras historias", CategoryKind.OTHER, it) }

        return campaignNodes + zoneNodes + listOfNotNull(otherNode)
    }

    /**
     * Detalle de una historia: nombre, zona y nivel de cada misión, en el orden
     * en que se hacen y marcando las opcionales. Sale de los assets, así que es
     * instantáneo y funciona sin conexión.
     */
    suspend fun storylineQuests(id: Int): List<StorylineQuest> {
        val def = file().storylines.firstOrNull { it.id == id } ?: return emptyList()
        val completed = completedQuestIds()
        val names = names()
        val meta = meta()
        val areaNames = areas()
        return def.questIds.mapIndexed { index, qid ->
            val m = meta[qid]
            StorylineQuest(
                id = qid,
                name = names[qid] ?: "Misión #$qid",
                completed = qid in completed,
                zone = m?.getOrNull(0)?.takeIf { it != 0 }?.let { areaNames[it] },
                minLevel = m?.getOrNull(1) ?: 0,
                description = null,
                step = index + 1,
                optional = qid in def.opt,
            )
        }
    }

    /**
     * Descripción y recompensas de una misión concreta, con imagen de cada objeto.
     * Se pide a la API solo al abrir el detalle, para no gastar una petición por
     * misión al listar la cadena.
     *
     * La recompensa de misión no es aleatoria: si hay un solo objeto está
     * garantizado y, si hay varios, el juego te deja elegir uno.
     */
    /**
     * Nombre y estado de una lista de misiones, con las completadas primero.
     * Lo usan las semanales para enseñar de qué misiones se componen.
     */
    suspend fun questsFor(questIds: List<Int>, limit: Int = 12): List<WeeklyQuestDone> {
        if (questIds.isEmpty()) return emptyList()
        val names = names()
        val completed = completedQuestIds()
        return questIds
            .sortedByDescending { it in completed }
            .take(limit)
            .map { WeeklyQuestDone(it, names[it] ?: "#$it", it in completed) }
    }

    /** Ficha completa de una misión, mire desde donde se mire en la app. */
    suspend fun fullDetail(questId: Int): QuestFullDetail {
        val names = names()
        val meta = meta()[questId]
        val areaNames = areas()
        val completed = completedQuestIds()
        val name = names[questId] ?: "Misión #$questId"
        val story = file().storylines.firstOrNull { questId in it.questIds }
        val detail = questDetail(questId)
        return QuestFullDetail(
            id = questId,
            name = name,
            completed = questId in completed,
            zone = meta?.getOrNull(0)?.takeIf { it != 0 }?.let { areaNames[it] },
            minLevel = meta?.getOrNull(1) ?: 0,
            storyline = story?.label(spanish()),
            description = detail.description,
            rewards = detail.rewards,
            tomTom = tomTomCommand(questId, name),
        )
    }

    suspend fun questDetail(questId: Int): QuestDetail {
        questDetailCache[questId]?.let { return it }
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        val detail = runCatching { api.quest(questId, region.namespaceStatic) }.getOrNull()
        val items = detail?.rewards?.items?.items?.mapNotNull { it.item }
            ?.filter { it.id != 0 }.orEmpty()
        val chance: DropChance =
            if (items.size > 1) DropChance.Choice(items.size) else DropChance.Guaranteed
        val rewards = items.map { item ->
            val icon = runCatching { api.itemMedia(item.id, region.namespaceStatic) }
                .getOrNull()?.assets?.firstOrNull { it.key == "icon" }?.value
            QuestReward(
                itemId = item.id,
                name = item.name ?: "Objeto #${item.id}",
                iconUrl = icon,
                chancePercent = DropChanceCalculator.percent(chance),
                chanceExplanation = DropChanceCalculator.explain(chance),
            )
        }
        val out = QuestDetail(detail?.description?.takeIf { it.isNotBlank() }, rewards)
        questDetailCache[questId] = out
        return out
    }
}

data class QuestDetail(val description: String?, val rewards: List<QuestReward>)

/** Todo lo que la app sabe de una misión, para la pantalla de detalle. */
data class QuestFullDetail(
    val id: Int,
    val name: String,
    val completed: Boolean,
    val zone: String?,
    val minLevel: Int,
    val storyline: String?,
    val description: String?,
    val rewards: List<QuestReward>,
    /** Comando /way de TomTom, o null si el cliente no publica el punto. */
    val tomTom: String?,
)
