package com.azeroth.companion.data

import android.content.Context
import com.azeroth.companion.core.database.SnapshotDao
import com.azeroth.companion.core.datastore.LanguagePref
import com.azeroth.companion.core.datastore.SettingsRepository
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
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Una mazmorra Mythic+ terminada después del último reset. */
@Serializable
data class MythicRunRecord(val name: String = "", val level: Int = 0, val inTime: Boolean = true)

/** Un jefe de banda derrotado después del último reset, con su dificultad. */
@Serializable
data class RaidKillRecord(
    val name: String = "",
    val difficulty: String = "",
    /** Instancia del jefe: distingue la banda de temporada de un jefe de mundo. */
    val instanceId: Int = 0,
)

data class WeeklyQuestDone(val id: Int, val name: String, val completed: Boolean = true)

/**
 * Lo que el personaje ha hecho DESDE el último reset, con datos verificables de
 * la API: cada mazmorra M+ y cada jefe traen su fecha, así que no hace falta
 * ninguna línea base. Las Delves y las misiones sí son acumuladas en la API y se
 * calculan por diferencia contra el último snapshot anterior al reset; mientras
 * no exista ese snapshot la app lo dice en lugar de enseñar un cero engañoso.
 */
data class WeeklyActivity(
    val mythicRuns: List<MythicRunRecord> = emptyList(),
    val raidKills: List<RaidKillRecord> = emptyList(),
    val delves: Int? = null,
    val quests: List<WeeklyQuestDone> = emptyList(),
    val hasBaseline: Boolean = false,
    val syncedAt: Instant? = null,
    val hasCharacter: Boolean = false,
    /** Semanales repetibles APRENDIDAS que figuran completadas ahora mismo. */
    val repeatableDone: List<WeeklyQuestDone> = emptyList(),
    /** Cuántas repetibles conoce ya la app (0 = todavía aprendiendo). */
    val learnedRepeatables: Int = 0,
)

@Singleton
class WeeklyActivityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshotDao: SnapshotDao,
    private val activeCharacter: ActiveCharacter,
    private val settingsRepository: SettingsRepository,
    private val eventsRepository: EventsRepository,
    private val apiFactory: BlizzardApiFactory,
    private val repeatableQuestDao: com.azeroth.companion.core.database.RepeatableQuestDao,
    private val json: Json,
) {
    private var questNames: Map<Int, String>? = null
    private val fetched = mutableMapOf<Int, String>()

    private suspend fun names(): Map<Int, String> = withContext(Dispatchers.IO) {
        questNames?.let { return@withContext it }
        val spanish = (LanguagePref.read(context) ?: java.util.Locale.getDefault().language)
            .startsWith("es")
        val asset = if (spanish) "catalog/quests_es.json" else "catalog/quests_en.json"
        runCatching {
            json.decodeFromString(
                MapSerializer(String.serializer(), String.serializer()),
                context.assets.open(asset).bufferedReader().use { it.readText() },
            ).mapKeys { (k, _) -> k.toInt() }
        }.getOrDefault(emptyMap()).also { questNames = it }
    }

    suspend fun load(): WeeklyActivity {
        val character = activeCharacter.current() ?: return WeeklyActivity()
        val current = snapshotDao.latest(character.id) ?: return WeeklyActivity(hasCharacter = true)
        val lastReset = eventsRepository.resetClock().lastWeeklyReset(Instant.now())
        val baseline = snapshotDao.lastBefore(character.id, lastReset)

        val runs = decode(ListSerializer(MythicRunRecord.serializer()), current.mythicLevelsThisWeekJson)
        val kills = decode(ListSerializer(RaidKillRecord.serializer()), current.raidKillsThisWeekJson)

        val questIdsNow = decode(ListSerializer(Int.serializer()), current.completedQuestIdsJson).toSet()
        val questIdsBefore = baseline
            ?.let { decode(ListSerializer(Int.serializer()), it.completedQuestIdsJson) }
            ?.toSet()
        val newQuests = questIdsBefore?.let { (questIdsNow - it).sorted() }.orEmpty()

        // Una repetible que figura completada AHORA se hizo necesariamente en el
        // periodo actual, porque Blizzard las reinicia en cada reset. No hace
        // falta línea base ni acertar ningún ID a mano.
        val repeatable = repeatableQuestDao.ids().toSet()
        val repeatableDone = questIdsNow.filter { it in repeatable }.sorted()

        return WeeklyActivity(
            mythicRuns = runs.sortedByDescending { it.level },
            raidKills = kills,
            delves = baseline?.let {
                (current.delvesCompletedTotal - it.delvesCompletedTotal).coerceAtLeast(0)
            },
            quests = resolveNames(newQuests),
            hasBaseline = baseline != null,
            syncedAt = current.takenAt,
            hasCharacter = true,
            repeatableDone = resolveNames(repeatableDone),
            learnedRepeatables = repeatable.size,
        )
    }

    /**
     * Los nombres salen del catálogo horneado; las misiones que no pertenecen a
     * ninguna cadena (las semanales repetibles, sobre todo) no están ahí, así que
     * se piden a la API solo para esas pocas y se cachean en memoria.
     */
    private suspend fun resolveNames(ids: List<Int>): List<WeeklyQuestDone> {
        if (ids.isEmpty()) return emptyList()
        val baked = names()
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        return ids.take(MAX_QUESTS_SHOWN).map { id ->
            val name = baked[id] ?: fetched[id] ?: runCatching {
                api.quest(id, region.namespaceStatic).title
            }.getOrNull()?.also { fetched[id] = it } ?: "Misión #$id"
            WeeklyQuestDone(id, name)
        }
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, raw: String?): T =
        runCatching { json.decodeFromString(serializer, raw.orEmpty()) }.getOrElse {
            json.decodeFromString(serializer, "[]")
        }

    private companion object {
        /** Tope para no disparar decenas de peticiones al resolver nombres. */
        const val MAX_QUESTS_SHOWN = 60
    }
}
