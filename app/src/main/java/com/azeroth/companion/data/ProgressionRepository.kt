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
import kotlinx.serialization.builtins.ListSerializer
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
     * Gran Bóveda (§7.3). Banda y Mythic+ salen EXACTOS del perfil: la API marca
     * la fecha de cada muerte de jefe y de cada mazmorra, así que se cuenta lo
     * posterior al último reset y la recompensa prevista usa la dificultad/nivel
     * real de cada actividad. Solo Mundo queda deducido: la API no expone la
     * Bóveda ni las Delves de la semana, así que se calcula por diferencia de la
     * estadística acumulada de Delves contra el último snapshot previo al reset.
     */
    suspend fun computeVault(characterId: Long): GreatVaultProgress {
        val catalog = catalogRepository.load()
        val now = Instant.now()
        val lastReset = eventsRepository.resetClock().lastWeeklyReset(now)
        val current = snapshotDao.latest(characterId)

        // Un jefe cuenta UNA vez por semana, aunque se mate en varias
        // dificultades, y aporta el nivel de la mejor de ellas: es como funciona
        // la Bóveda en el juego. Antes se contaba cada par jefe+dificultad, así
        // que limpiar la banda en normal y en heroico daba 2x jefes y desbloqueaba
        // casillas que el juego no daba.
        val raidIlvls = VaultCalculator.raidTiersByBoss(
            decode(
                ListSerializer(RaidKillRecord.serializer()), current?.raidKillsThisWeekJson,
            ).map { kill ->
                kill.name to (
                    catalog.vault.raidIlvlByDifficulty[kill.difficulty]
                        ?: catalog.vault.raidSlotIlvl.firstOrNull() ?: 0
                    )
            },
        )
        val mythicIlvls = decode(
            ListSerializer(MythicRunRecord.serializer()), current?.mythicLevelsThisWeekJson,
        ).map { run ->
            // Llaves por encima de la tabla del catálogo premian como el techo.
            catalog.vault.mythicPlusIlvlByLevel[run.level.toString()]
                ?: catalog.vault.mythicPlusIlvlByLevel.entries
                    .filter { (it.key.toIntOrNull() ?: 0) <= run.level }
                    .maxByOrNull { it.key.toIntOrNull() ?: 0 }?.value
                ?: catalog.vault.mythicPlusSlotIlvl.first()
        }

        return VaultCalculator.vaultFromTiers(
            characterId = characterId,
            raidIlvls = raidIlvls,
            mythicIlvls = mythicIlvls,
            worldActivitiesThisWeek = worldActivities(characterId, lastReset, current, catalog.vault),
            rules = catalog.vault,
        )
    }

    /** Delves de la semana (por diferencia) más las tareas de mundo detectadas. */
    private suspend fun worldActivities(
        characterId: Long,
        lastReset: Instant,
        current: com.azeroth.companion.core.database.SnapshotEntity?,
        rules: com.azeroth.companion.core.catalog.VaultRules,
    ): Int {
        val baseline = snapshotDao.lastBefore(characterId, lastReset)
        val statDelta = if (baseline != null && current != null) {
            (current.delvesCompletedTotal - baseline.delvesCompletedTotal).coerceAtLeast(0)
        } else {
            0
        }
        // Cada Delve generosa tiene su "Se busca surcabismos" semanal, que
        // Blizzard reinicia en cada reset: contarlas da el dato exacto de la
        // semana sin necesitar una lectura previa. La estadística acumulada
        // sigue como respaldo por si el jugador hizo Delves sin la misión.
        val delveQuests = delveQuestIds()
        val fromQuests = if (delveQuests.isEmpty() || current == null) {
            0
        } else {
            decode(ListSerializer(Int.serializer()), current.completedQuestIdsJson)
                .count { it in delveQuests }
        }
        val delves = maxOf(statDelta, fromQuests)
        val states = taskStateDao.observeForCharacter(characterId).first()
        val tasks = states
            .filter { it.taskId in rules.worldContributingTaskIds }
            .filter { !it.periodStart.isBefore(lastReset) }
            .sumOf { it.completions }
        return delves + tasks
    }

    /** IDs de las misiones semanales de Delve declaradas en el catálogo. */
    private suspend fun delveQuestIds(): Set<Int> {
        val task = catalogRepository.load().weeklyTasks.firstOrNull { it.id == DELVE_TASK_ID }
            ?: return emptySet()
        return questIdsOf(task.detectionRule)
    }

    private fun questIdsOf(rule: com.azeroth.companion.core.model.DetectionRule): Set<Int> =
        when (rule) {
            is com.azeroth.companion.core.model.DetectionRule.QuestCompleted -> rule.questIds.toSet()
            is com.azeroth.companion.core.model.DetectionRule.AnyOf ->
                rule.rules.flatMap { questIdsOf(it) }.toSet()
            else -> emptySet()
        }

    /**
     * Si no hay snapshot anterior al reset NI misiones de Delve completadas, no
     * se puede saber cuántas Delves son de esta semana: la estadística del
     * perfil es acumulada. La UI lo dice en lugar de mostrar un 0 que parece un
     * dato real.
     */
    suspend fun worldBaselineMissing(characterId: Long): Boolean {
        val lastReset = eventsRepository.resetClock().lastWeeklyReset(Instant.now())
        if (snapshotDao.lastBefore(characterId, lastReset) != null) return false
        val current = snapshotDao.latest(characterId) ?: return true
        val delveQuests = delveQuestIds()
        return decode(ListSerializer(Int.serializer()), current.completedQuestIdsJson)
            .none { it in delveQuests }
    }

    private companion object {
        const val DELVE_TASK_ID = "weekly_delves"
    }

    private fun <T> decode(
        serializer: kotlinx.serialization.KSerializer<List<T>>,
        raw: String?,
    ): List<T> = runCatching {
        raw?.let { json.decodeFromString(serializer, it) }.orEmpty()
    }.getOrDefault(emptyList())
}
