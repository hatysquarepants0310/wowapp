package com.azeroth.companion.data

import android.content.Context
import com.azeroth.companion.core.catalog.CatalogRepository
import com.azeroth.companion.core.database.SnapshotDao
import com.azeroth.companion.core.datastore.LanguagePref
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.loot.DropChance
import com.azeroth.companion.core.loot.DropChanceCalculator
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
data class SeasonLootFile(
    val expansionId: Int = 0,
    val expansion: String = "",
    val bosses: List<BossLootDef> = emptyList(),
    val items: Map<String, LootItemDef> = emptyMap(),
    val mountItemIds: List<Int> = emptyList(),
    val petItemIds: List<Int> = emptyList(),
)

@Serializable
data class BossLootDef(
    val kind: String = "",
    val instanceId: Int = 0,
    val instance: String = "",
    val bossId: Int = 0,
    val boss: String = "",
    val bossEn: String = "",
    val modes: List<String> = emptyList(),
    val items: List<Int> = emptyList(),
)

@Serializable
data class LootItemDef(
    val id: Int = 0,
    val name: String = "",
    val nameEn: String = "",
    val quality: String = "",
    val cls: Int = 0,
    val sub: Int = 0,
    val slot: String = "",
    val icon: String? = null,
)

/** Un objeto con su origen y su probabilidad, listo para pintar. */
data class LootEntry(
    val itemId: Int,
    val name: String,
    val quality: String,
    val iconUrl: String?,
    val slot: String?,
    val instance: String,
    val instanceId: Int,
    val boss: String,
    val bossId: Int,
    val difficulties: List<String>,
    val chance: DropChance,
    val chancePercent: Double?,
    val chanceExplanation: String,
    val isMount: Boolean,
    val owned: Boolean = false,
    /** Veces que has matado al jefe del que cae (de tu propio perfil). */
    val attempts: Int = 0,
    /** Probabilidad de que a estas alturas ya lo tuvieras, dado [attempts]. */
    val cumulativePercent: Double? = null,
)

/**
 * Botín de la temporada actual desde el journal OFICIAL de Blizzard, horneado en
 * assets por tools/build_season_loot.py: la app no gasta ni una petición para
 * listar 385 objetos con su imagen, su origen y su probabilidad estimada.
 */
@Singleton
class SeasonLootRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val snapshotDao: SnapshotDao,
    private val activeCharacter: ActiveCharacter,
    private val apiFactory: com.azeroth.companion.core.network.BlizzardApiFactory,
    private val json: Json,
) {
    private var fileCache: SeasonLootFile? = null
    private var mountItemToMountId: Map<Int, Int>? = null

    private suspend fun file(): SeasonLootFile = withContext(Dispatchers.IO) {
        fileCache?.let { return@withContext it }
        runCatching {
            json.decodeFromString(
                SeasonLootFile.serializer(),
                context.assets.open("catalog/season_loot.json").bufferedReader().use { it.readText() },
            )
        }.getOrDefault(SeasonLootFile()).also { fileCache = it }
    }

    private fun spanish(): Boolean =
        (LanguagePref.read(context) ?: java.util.Locale.getDefault().language).startsWith("es")

    private fun LootItemDef.label(spanish: Boolean) =
        if (spanish) name.ifBlank { nameEn } else nameEn.ifBlank { name }

    /** Nombre de la expansión de la que salen estos datos. */
    suspend fun expansionName(): String = file().expansion

    /**
     * Monturas exclusivas de la temporada (las que aparecen en las tablas de
     * botín de bandas y mazmorras de la expansión actual), marcando las que ya
     * tienes, y con TUS intentos: la API dice cuántas veces has matado
     * a cada jefe, así que se puede decir "llevas 23 intentos" con datos reales
     * en lugar de solo una probabilidad teórica.
     */
    suspend fun seasonMounts(): List<LootEntry> {
        val base = entriesFor { it in file().mountItemIds }
        val kills = killsByEncounter()
        return base.map { entry ->
            val attempts = kills[entry.bossId] ?: 0
            entry.copy(
                attempts = attempts,
                cumulativePercent = DropChanceCalculator.cumulative(entry.chance, attempts),
            )
        }
    }

    /** encounterId -> veces matado (sumando dificultades), del perfil del personaje. */
    private suspend fun killsByEncounter(): Map<Int, Int> {
        val character = activeCharacter.current() ?: return emptyMap()
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        val raids = runCatching {
            api.raidEncounters(character.realmSlug, character.name.lowercase(), region.namespaceProfile)
        }.getOrNull() ?: return emptyMap()
        val out = mutableMapOf<Int, Int>()
        raids.expansions.flatMap { it.instances }.forEach { inst ->
            inst.modes.forEach { mode ->
                mode.progress?.encounters.orEmpty().forEach { enc ->
                    out[enc.encounter.id] = (out[enc.encounter.id] ?: 0) + enc.completed_count
                }
            }
        }
        return out
    }

    /**
     * Armas y equipo destacado de la temporada: lo épico o mejor de los jefes
     * finales, que es lo que la gente persigue.
     */
    suspend fun highlightGear(limit: Int = 40): List<LootEntry> {
        val f = file()
        val finalBossIds = f.bosses
            .groupBy { it.instanceId }
            .mapNotNull { (_, list) -> list.maxByOrNull { it.bossId }?.bossId }
            .toSet()
        val fromFinalBosses = f.bosses.filter { it.bossId in finalBossIds }
            .flatMap { boss -> boss.items.map { it to boss } }
            .filter { (id, _) ->
                val item = f.items[id.toString()]
                item != null && item.quality in EPIC_OR_BETTER && item.slot != "NON_EQUIP"
            }
        return fromFinalBosses
            .distinctBy { it.first }
            .take(limit)
            .map { (id, boss) -> entry(id, boss, f, emptySet()) }
            .sortedWith(compareByDescending<LootEntry> { it.isMount }.thenBy { it.name })
    }

    /**
     * Lo destacado del botín de unas instancias: las monturas primero y luego el
     * equipo épico. Responde "¿por qué hago esta semanal?".
     */
    suspend fun instanceHighlights(instanceIds: List<Int>, limit: Int = 8): List<LootEntry> {
        if (instanceIds.isEmpty()) return emptyList()
        val f = file()
        val owned = ownedMountItemIds()
        return f.bosses.filter { it.instanceId in instanceIds }
            .flatMap { boss -> boss.items.map { it to boss } }
            .distinctBy { it.first }
            .map { (id, boss) -> entry(id, boss, f, owned) }
            .filter { it.isMount || it.quality in EPIC_OR_BETTER }
            .sortedWith(compareByDescending<LootEntry> { it.isMount }.thenBy { it.name })
            .take(limit)
    }

    /**
     * Botín de un jefe concreto, con imagen y probabilidad. La temporada actual
     * está horneada (respuesta instantánea); para el contenido antiguo se pide al
     * journal y se cachea, así que la función sirve para CUALQUIER jefe del juego.
     */
    suspend fun bossLoot(bossId: Int, isRaid: Boolean = true): List<LootEntry> {
        val f = file()
        val owned = ownedMountItemIds()
        f.bosses.firstOrNull { it.bossId == bossId }?.let { boss ->
            return boss.items.map { entry(it, boss, f, owned) }
                .sortedWith(compareByDescending<LootEntry> { it.isMount }.thenBy { it.name })
        }
        return remoteBossLoot(bossId, isRaid)
    }

    private val remoteCache = mutableMapOf<Int, List<LootEntry>>()

    private suspend fun remoteBossLoot(bossId: Int, isRaid: Boolean): List<LootEntry> {
        remoteCache[bossId]?.let { return it }
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        val encounter = runCatching {
            api.journalEncounter(bossId, region.namespaceStatic)
        }.getOrNull() ?: return emptyList()
        val itemIds = encounter.items.mapNotNull { it.item?.id }.filter { it != 0 }
        val rules = catalogRepository.load().loot
        val entries = itemIds.map { itemId ->
            val detail = runCatching { api.item(itemId, region.namespaceStatic) }.getOrNull()
            val icon = runCatching { api.itemMedia(itemId, region.namespaceStatic) }
                .getOrNull()?.assets?.firstOrNull { it.key == "icon" }?.value
            val isMount = detail?.item_class?.id == MOUNT_CLASS && detail.item_subclass?.id == MOUNT_SUBCLASS
            val chance = DropChanceCalculator.forBossItem(
                itemId = itemId,
                tableSize = itemIds.size,
                isMount = isMount,
                isRaid = isRaid,
                isFinalBoss = false,
                rules = rules,
            )
            LootEntry(
                itemId = itemId,
                name = detail?.name ?: encounter.items.firstOrNull { it.item?.id == itemId }
                    ?.item?.name ?: "Objeto #$itemId",
                quality = detail?.quality?.type.orEmpty(),
                iconUrl = icon,
                slot = detail?.inventory_type?.type?.takeIf { it != "NON_EQUIP" },
                instance = encounter.instance?.name.orEmpty(),
                instanceId = encounter.instance?.id ?: 0,
                boss = encounter.name,
                bossId = bossId,
                difficulties = encounter.modes.map { it.type },
                chance = chance,
                chancePercent = DropChanceCalculator.percent(chance),
                chanceExplanation = DropChanceCalculator.explain(chance),
                isMount = isMount,
            )
        }.sortedWith(compareByDescending<LootEntry> { it.isMount }.thenBy { it.name })
        remoteCache[bossId] = entries
        return entries
    }

    private suspend fun entriesFor(predicate: suspend (Int) -> Boolean): List<LootEntry> {
        val f = file()
        val owned = ownedMountItemIds()
        val out = mutableListOf<LootEntry>()
        f.bosses.forEach { boss ->
            boss.items.forEach { id ->
                if (predicate(id) && out.none { it.itemId == id }) {
                    out += entry(id, boss, f, owned)
                }
            }
        }
        return out.sortedBy { it.name }
    }

    private suspend fun entry(
        itemId: Int,
        boss: BossLootDef,
        f: SeasonLootFile,
        ownedItemIds: Set<Int>,
    ): LootEntry {
        val es = spanish()
        val item = f.items[itemId.toString()]
        val isMount = itemId in f.mountItemIds
        val isFinal = f.bosses.filter { it.instanceId == boss.instanceId }
            .maxByOrNull { it.bossId }?.bossId == boss.bossId
        val chance = DropChanceCalculator.forBossItem(
            itemId = itemId,
            tableSize = boss.items.size,
            isMount = isMount,
            isRaid = boss.kind == "RAID",
            isFinalBoss = isFinal,
            rules = catalogRepository.load().loot,
        )
        return LootEntry(
            itemId = itemId,
            name = item?.label(es) ?: "Objeto #$itemId",
            quality = item?.quality.orEmpty(),
            iconUrl = item?.icon,
            slot = item?.slot?.takeIf { it.isNotBlank() && it != "NON_EQUIP" },
            instance = boss.instance,
            instanceId = boss.instanceId,
            boss = if (es) boss.boss else boss.bossEn.ifBlank { boss.boss },
            bossId = boss.bossId,
            difficulties = boss.modes,
            chance = chance,
            chancePercent = DropChanceCalculator.percent(chance),
            chanceExplanation = DropChanceCalculator.explain(chance),
            isMount = isMount,
            owned = itemId in ownedItemIds,
        )
    }

    /**
     * Los objetos-montura y las monturas de la colección son IDs distintos, así
     * que se cruzan por nombre: es el único puente que da la API sin pedir cada
     * montura una por una.
     */
    private suspend fun ownedMountItemIds(): Set<Int> {
        val f = file()
        if (f.mountItemIds.isEmpty()) return emptySet()
        val character = activeCharacter.current() ?: return emptySet()
        val snapshot = snapshotDao.latest(character.id) ?: return emptySet()
        val ownedMountIds = runCatching {
            json.decodeFromString(ListSerializer(Int.serializer()), snapshot.mountIdsJson).toSet()
        }.getOrDefault(emptySet())
        if (ownedMountIds.isEmpty()) return emptySet()
        val byName = mountNameToItemId()
        return ownedMountIds.mapNotNull { byName[it] }.toSet()
    }

    /**
     * mountId → itemId, resuelto con el catálogo de monturas horneado. Sin este
     * puente no se puede saber si ya tienes la montura de la temporada.
     */
    private suspend fun mountNameToItemId(): Map<Int, Int> = withContext(Dispatchers.IO) {
        mountItemToMountId?.let { return@withContext it }
        val f = file()
        // Se indexa por los DOS idiomas: así el cruce funciona sea cual sea el
        // idioma con el que se generó cada asset.
        val byName = mutableMapOf<String, Int>()
        f.mountItemIds.forEach { id ->
            f.items[id.toString()]?.let { item ->
                item.name.takeIf { it.isNotBlank() }?.let { byName[it.lowercase()] = id }
                item.nameEn.takeIf { it.isNotBlank() }?.let { byName[it.lowercase()] = id }
            }
        }
        val catalog = runCatching {
            json.decodeFromString(
                MountCatalog.serializer(),
                context.assets.open("catalog/mounts.json").bufferedReader().use { it.readText() },
            )
        }.getOrNull()
        val out = mutableMapOf<Int, Int>()
        listOf(catalog?.namesEs, catalog?.names).forEach { table ->
            table.orEmpty().forEach { (mountId, mountName) ->
                val itemId = byName[mountName.lowercase()] ?: return@forEach
                mountId.toIntOrNull()?.let { out[it] = itemId }
            }
        }
        out.toMap().also { mountItemToMountId = it }
    }

    private companion object {
        val EPIC_OR_BETTER = setOf("EPIC", "LEGENDARY", "ARTIFACT", "HEIRLOOM")

        /** item_class 15 / subclase 5 = montura, en la taxonomía de Blizzard. */
        const val MOUNT_CLASS = 15
        const val MOUNT_SUBCLASS = 5
    }
}

/** Solo la parte de mounts.json que hace falta aquí. */
@Serializable
data class MountCatalog(
    val names: Map<String, String> = emptyMap(),
    val namesEs: Map<String, String> = emptyMap(),
)
