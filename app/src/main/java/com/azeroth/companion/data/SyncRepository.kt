package com.azeroth.companion.data

import com.azeroth.companion.core.database.CharacterDao
import com.azeroth.companion.core.database.CharacterEntity
import com.azeroth.companion.core.database.SnapshotDao
import com.azeroth.companion.core.database.SnapshotEntity
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.detection.DetectionEngine
import com.azeroth.companion.core.detection.SnapshotView
import com.azeroth.companion.core.model.Region
import com.azeroth.companion.core.network.BlizzardApiFactory
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncResult {
    data object Success : SyncResult
    data object NotLoggedIn : SyncResult
    data class Failed(val reason: String) : SyncResult
}

/**
 * Sincronización con la API de Blizzard (§10). Cada sync guarda un snapshot
 * inmutable y ejecuta el motor de detección contra el primero posterior al
 * último reset (§6). Todo fallo degrada, nunca tumba: los datos locales y los
 * temporizadores siguen siendo la fuente de la UI.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val apiFactory: BlizzardApiFactory,
    private val settingsRepository: SettingsRepository,
    private val characterDao: CharacterDao,
    private val snapshotDao: SnapshotDao,
    private val seasonalGoalDao: com.azeroth.companion.core.database.SeasonalGoalDao,
    private val weeklyRepository: WeeklyRepository,
    private val eventsRepository: EventsRepository,
    private val catalogRepository: com.azeroth.companion.core.catalog.CatalogRepository,
    private val detectionEngine: DetectionEngine,
    private val activeCharacter: ActiveCharacter,
    private val repeatableQuestDao: com.azeroth.companion.core.database.RepeatableQuestDao,
    private val json: Json,
) {

    /** Importa la lista de personajes de la cuenta al roster local. */
    suspend fun syncRoster(): SyncResult {
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        return runCatching {
            val profile = api.userProfile(namespace = region.namespaceProfile)
            // El endpoint de cuenta NO trae ilvl ni spec. Fusionar con lo ya
            // guardado: escribir ceros aquí borraba los datos del sync detallado
            // en cada refresco del roster (bug de "Nivel 0").
            val existing = characterDao.observeAll().first().associateBy { it.id }
            profile.wow_accounts.flatMap { it.characters }.forEach { dto ->
                val prev = existing[dto.id]
                characterDao.upsert(
                    CharacterEntity(
                        id = dto.id,
                        name = dto.name,
                        realmSlug = dto.realm.slug,
                        realmName = dto.realm.name ?: dto.realm.slug,
                        region = region.name,
                        faction = dto.faction?.type ?: prev?.faction ?: "NEUTRAL",
                        playableClass = dto.playable_class?.name?.takeIf { it.isNotBlank() }
                            ?: prev?.playableClass.orEmpty(),
                        activeSpec = prev?.activeSpec,
                        level = dto.level.takeIf { it > 0 } ?: prev?.level ?: 0,
                        averageItemLevel = prev?.averageItemLevel ?: 0,
                        equippedItemLevel = prev?.equippedItemLevel ?: 0,
                        isMain = prev?.isMain ?: false,
                        lastLogin = prev?.lastLogin,
                        lastSyncedAt = prev?.lastSyncedAt,
                    ),
                )
            }
            SyncResult.Success
        }.getOrElse { error ->
            // /profile/user/wow es el ÚNICO endpoint que exige el token del usuario.
            // Si la sesión caducó, el roster ya guardado sigue siendo válido y el
            // resto del sync funciona con el token de aplicación: no es un fallo
            // que deba interrumpir nada.
            val haveRoster = characterDao.observeAll().first().isNotEmpty()
            if ((error as? HttpException)?.code() == 401 && haveRoster) {
                SyncResult.Success
            } else {
                error.toSyncFailure()
            }
        }
    }

    /** Sync completo del personaje activo: perfil + snapshot + detección. */
    suspend fun syncActiveCharacter(): SyncResult {
        val settings = settingsRepository.settings.first()
        val region = settings.region
        val character = activeCharacter.current()
            ?: return SyncResult.Failed("Sin personaje activo. Sincroniza el roster primero.")
        val api = apiFactory.forRegion(region)
        val namespace = region.namespaceProfile
        val realm = character.realmSlug
        val name = character.name.lowercase()

        return runCatching {
            val profile = api.characterProfile(realm, name, namespace)
            val quests = runCatching { api.completedQuests(realm, name, namespace) }.getOrNull()
            val reps = runCatching { api.reputations(realm, name, namespace) }.getOrNull()
            val mythic = runCatching { api.mythicKeystoneProfile(realm, name, namespace) }.getOrNull()
            val raids = runCatching { api.raidEncounters(realm, name, namespace) }.getOrNull()
            val achievements = runCatching { api.achievements(realm, name, namespace) }.getOrNull()
            val mounts = runCatching { api.mounts(realm, name, namespace) }.getOrNull()
            val stats = runCatching { api.statistics(realm, name, namespace) }.getOrNull()
            val media = runCatching { api.characterMedia(realm, name, namespace) }.getOrNull()

            val now = Instant.now()
            val clock = eventsRepository.resetClock()
            val lastReset = clock.lastWeeklyReset(now)

            characterDao.upsert(
                character.copy(
                    level = profile.level,
                    averageItemLevel = profile.average_item_level,
                    equippedItemLevel = profile.equipped_item_level,
                    activeSpec = profile.active_spec?.name,
                    lastLogin = profile.last_login_timestamp?.let(Instant::ofEpochMilli),
                    lastSyncedAt = now,
                    isInactive = false,
                    // Se conserva el render anterior si esta vez no vino: es
                    // preferible una imagen de hace un rato a un hueco.
                    renderUrl = media?.render ?: character.renderUrl,
                    avatarUrl = media?.avatar ?: character.avatarUrl,
                ),
            )

            // El periodo vigente lo dice Blizzard, no nuestro reloj.
            //
            // BUG que esto arregla: la app filtraba `current_period.best_runs`
            // por su propio `lastReset` y devolvía 0 con la bóveda llena.
            // Blizzard solo refresca el perfil cuando el personaje se conecta,
            // así que tras un reset el perfil sigue anunciando el periodo
            // anterior; comprobado con un personaje real: 8 llaves dentro de
            // `current_period`, todas descartadas por ser anteriores al reset
            // recién ocurrido.
            //
            // Si el periodo del perfil ES el vigente, sus llaves son las de esta
            // semana y se cuentan todas: Blizzard ya las acotó. Si es anterior,
            // el perfil está desfasado y hay que decirlo, no enseñar un 0.
            val livePeriod = runCatching {
                api.mythicPeriodIndex(region.namespaceDynamic).current_period?.id
            }.getOrNull()
            val profilePeriod = mythic?.current_period?.period?.id
            val periodIsCurrent = livePeriod == null || profilePeriod == null ||
                profilePeriod == livePeriod
            val freshRuns = if (periodIsCurrent) {
                mythic?.current_period?.best_runs.orEmpty()
            } else {
                emptyList()
            }
            val runsThisWeek = freshRuns.size
            // El perfil no refleja la semana en curso si el personaje no se ha
            // conectado desde el reset: entonces no se puede afirmar nada.
            val profileStale = !periodIsCurrent ||
                (profile.last_login_timestamp?.let { it < lastReset.toEpochMilli() } ?: false)
            val raidKills = raids?.expansions?.flatMap { it.instances }
                ?.associate { it.instance.id to it.modes.sumOf { m -> m.progress?.completed_count ?: 0 } }
                ?: emptyMap()

            // Datos EXACTOS de la semana para la Gran Bóveda. `last_kill_timestamp`
            // por jefe y dificultad permite saber qué cayó tras el reset sin
            // depender de tener un snapshot anterior: antes se calculaba como
            // delta contra la línea base y, si el primer sync de la semana era
            // posterior a la actividad, el resultado era siempre 0.
            val killsThisWeek = raids?.expansions
                ?.flatMap { it.instances }
                ?.flatMap { inst ->
                    inst.modes.flatMap { mode ->
                        val difficulty = mode.difficulty?.type.orEmpty()
                        mode.progress?.encounters.orEmpty()
                            .filter { it.last_kill_timestamp >= lastReset.toEpochMilli() }
                            .map { RaidKillRecord(it.encounter.name.orEmpty(), difficulty, inst.instance.id) }
                    }
                }.orEmpty()
            val mythicRunsRecords = freshRuns
                .map {
                    MythicRunRecord(
                        name = it.dungeon?.name.orEmpty(),
                        level = it.keystone_level,
                        inTime = it.is_completed_within_time,
                    )
                }.orEmpty()
            val catalog = catalogRepository.load()
            val allStats = stats?.categories?.flatMap { it.flatten() }.orEmpty()
            val delvesTotal = allStats
                .firstOrNull { it.id == catalog.vault.delveStatisticId }
                ?.quantity?.toInt() ?: 0
            // Solo las estadísticas que alguna regla necesita: guardar las 1123
                // que devuelve la API sería inflar cada snapshot sin motivo.
            val trackedStats = allStats
                .filter { it.id in catalog.trackedStatisticIds }
                .associate { it.id to it.quantity.toInt() }

            // ANTES de guardar el nuevo snapshot: aprender qué misiones son
            // repetibles. Una misión que estaba completada y ya no lo está solo
            // puede haber sido reiniciada por Blizzard, así que es semanal o
            // diaria. Así la app descubre las semanales del jugador sin depender
            // de una lista de IDs escrita a mano, que es justo lo que falló.
            // La primera vez se recorre TODO el histórico ya guardado: si la app
            // lleva semanas instalada, las semanales se conocen desde el primer
            // sync tras actualizar, sin esperar a que pase otro reset.
            backfillRepeatableQuests(character.id)
            learnRepeatableQuests(character.id, quests?.quests?.map { it.id }.orEmpty())

            snapshotDao.insert(
                SnapshotEntity(
                    characterId = character.id,
                    takenAt = now,
                    completedQuestIdsJson = json.encodeToString(
                        ListSerializer(Int.serializer()),
                        quests?.quests?.map { it.id }.orEmpty(),
                    ),
                    reputationsJson = json.encodeToString(
                        MapSerializer(Int.serializer(), Int.serializer()),
                        reps?.reputations?.associate { it.faction.id to it.standing.raw }.orEmpty(),
                    ),
                    mythicPlusRunsThisWeek = runsThisWeek,
                    raidKillsJson = json.encodeToString(
                        MapSerializer(Int.serializer(), Int.serializer()),
                        raidKills,
                    ),
                    achievementIdsJson = json.encodeToString(
                        ListSerializer(Int.serializer()),
                        achievements?.achievements?.filter { it.completed_timestamp != null }
                            ?.map { it.id }.orEmpty(),
                    ),
                    mountIdsJson = json.encodeToString(
                        ListSerializer(Int.serializer()),
                        mounts?.mounts?.map { it.mount.id }.orEmpty(),
                    ),
                    raidKillsThisWeekJson = json.encodeToString(
                        ListSerializer(RaidKillRecord.serializer()), killsThisWeek,
                    ),
                    mythicLevelsThisWeekJson = json.encodeToString(
                        ListSerializer(MythicRunRecord.serializer()), mythicRunsRecords,
                    ),
                    delvesCompletedTotal = delvesTotal,
                    statisticsJson = json.encodeToString(
                        MapSerializer(Int.serializer(), Int.serializer()), trackedStats,
                    ),
                    profileStale = profileStale,
                ),
            )
            snapshotDao.pruneOlderThan(now.minus(Duration.ofDays(21)))

            runDetection(character.id, lastReset)
            crossCheckSeasonalRewards(character.id)
            SyncResult.Success
        }.getOrElse { error ->
            if ((error as? HttpException)?.code() == 404) {
                // Personaje no encontrado: inactivo, sin borrar histórico (§11).
                characterDao.markInactive(character.id)
            }
            error.toSyncFailure()
        }
    }

    /**
     * Recorre el histórico de snapshots comparando cada par consecutivo. Se hace
     * una sola vez (mientras no haya nada aprendido): recupera de golpe las
     * semanales que el usuario ya hizo en semanas anteriores.
     */
    private suspend fun backfillRepeatableQuests(characterId: Long) {
        if (repeatableQuestDao.count() > 0) return
        val history = snapshotDao.allFor(characterId)
        if (history.size < 2) return
        val now = Instant.now()
        val learned = mutableSetOf<Int>()
        var previous: Set<Int>? = null
        history.forEach { snapshot ->
            val ids = runCatching {
                json.decodeFromString(ListSerializer(Int.serializer()), snapshot.completedQuestIdsJson)
            }.getOrDefault(emptyList()).toSet()
            if (ids.isNotEmpty()) {
                previous?.let { learned += (it - ids) }
                previous = ids
            }
        }
        if (learned.isEmpty()) return
        repeatableQuestDao.upsertAll(
            learned.map {
                com.azeroth.companion.core.database.RepeatableQuestEntity(questId = it, learnedAt = now)
            },
        )
    }

    /**
     * Compara el último snapshot con lo que la API devuelve ahora: lo que
     * desapareció de la lista de completadas es, por definición, repetible.
     */
    private suspend fun learnRepeatableQuests(characterId: Long, currentIds: List<Int>) {
        val previous = snapshotDao.latest(characterId) ?: return
        val before = runCatching {
            json.decodeFromString(ListSerializer(Int.serializer()), previous.completedQuestIdsJson)
        }.getOrDefault(emptyList()).toSet()
        if (before.isEmpty() || currentIds.isEmpty()) return
        val vanished = before - currentIds.toSet()
        if (vanished.isEmpty()) return
        val now = Instant.now()
        repeatableQuestDao.upsertAll(
            vanished.map {
                com.azeroth.companion.core.database.RepeatableQuestEntity(questId = it, learnedAt = now)
            },
        )
    }

    /** Evalúa cada regla de detección y persiste resultados (sin pisar overrides). */
    private suspend fun runDetection(characterId: Long, lastReset: Instant) {
        val baseline = snapshotDao.firstAfter(characterId, lastReset)?.toView()
        val preReset = snapshotDao.lastBefore(characterId, lastReset)
        val latest = snapshotDao.latest(characterId)
        val repeatable = repeatableQuestDao.ids().toSet()
        val worldBossInstances = catalogRepository.load().weeklyTasks
            .filter { it.category == com.azeroth.companion.core.model.TaskCategory.WORLD_BOSS }
            .flatMap { it.lootInstanceIds }
            .toSet()
        val current = latest?.toView()?.let { view ->
            // Un jefe matado en varias dificultades sigue siendo un jefe: para
            // contar la actividad de la semana se agrupa por nombre e instancia.
            val kills = decodeList(
                ListSerializer(RaidKillRecord.serializer()), latest.raidKillsThisWeekJson,
            ).distinctBy { it.instanceId to it.name }
            view.copy(
                // Un jefe de mundo es una instancia de un solo jefe: si se cuenta
                // junto a la banda, la fila de banda se marcaría por matar al de mundo.
                raidBossKillsThisWeek = kills.count { it.instanceId !in worldBossInstances },
                worldBossKillsThisWeek = kills.count { it.instanceId in worldBossInstances },
                statistics = decodeMap(latest.statisticsJson),
                statisticsBeforeReset = preReset?.let { decodeMap(it.statisticsJson) }.orEmpty(),
                questsBeforeReset = preReset?.let {
                    decodeList(ListSerializer(Int.serializer()), it.completedQuestIdsJson).toSet()
                },
                delvesThisWeek = preReset?.let {
                    (latest.delvesCompletedTotal - it.delvesCompletedTotal).coerceAtLeast(0)
                } ?: 0,
                // Una misión repetible que figura completada AHORA solo puede
                // haberse hecho en el periodo actual: Blizzard las reinicia.
                repeatableQuestsDoneThisWeek = view.completedQuestIds.count { it in repeatable },
            )
        }
        weeklyRepository.tasks(includeLegacy = true).forEach { task ->
            val result = detectionEngine.evaluate(task.detectionRule, baseline, current)
            if (result.completions > 0) {
                weeklyRepository.setDetectedCompletions(
                    characterId, task, result.completions, result.confidence,
                )
            }
        }
    }

    /**
     * Cross-check con colecciones (§8.2): recompensas de temporada con logro o
     * montura asociada se marcan obtenidas automáticamente, sin tocar las que
     * el usuario marcó a mano.
     */
    private suspend fun crossCheckSeasonalRewards(characterId: Long) {
        val snapshot = snapshotDao.latest(characterId)?.toView() ?: return
        catalogRepository.load().seasonalRewards.forEach { reward ->
            val obtained =
                (reward.achievementId != null && reward.achievementId in snapshot.achievementIds) ||
                    (reward.mountId != null && reward.mountId in snapshot.mountIds)
            if (obtained) {
                val existing = seasonalGoalDao.get(reward.id)
                if (existing?.obtained != true) {
                    seasonalGoalDao.upsert(
                        com.azeroth.companion.core.database.SeasonalGoalEntity(
                            rewardId = reward.id,
                            targeted = existing?.targeted ?: false,
                            obtained = true,
                            updatedAt = Instant.now(),
                        ),
                    )
                }
            }
        }
    }

    private fun decodeMap(raw: String?): Map<Int, Int> = runCatching {
        raw?.let {
            json.decodeFromString(MapSerializer(Int.serializer(), Int.serializer()), it)
        }.orEmpty()
    }.getOrDefault(emptyMap())

    private fun <T> decodeList(
        serializer: kotlinx.serialization.KSerializer<List<T>>,
        raw: String?,
    ): List<T> = runCatching {
        raw?.let { json.decodeFromString(serializer, it) }.orEmpty()
    }.getOrDefault(emptyList())

    private fun SnapshotEntity.toView() = SnapshotView(
        completedQuestIds = json.decodeFromString(
            ListSerializer(Int.serializer()), completedQuestIdsJson,
        ).toSet(),
        reputations = json.decodeFromString(
            MapSerializer(Int.serializer(), Int.serializer()), reputationsJson,
        ),
        mythicPlusRunsThisWeek = mythicPlusRunsThisWeek,
        raidKills = json.decodeFromString(
            MapSerializer(Int.serializer(), Int.serializer()), raidKillsJson,
        ),
        achievementIds = json.decodeFromString(
            ListSerializer(Int.serializer()), achievementIdsJson,
        ).toSet(),
        mountIds = json.decodeFromString(
            ListSerializer(Int.serializer()), mountIdsJson,
        ).toSet(),
    )

    private fun Throwable.toSyncFailure(): SyncResult.Failed = SyncResult.Failed(
        when (this) {
            is HttpException -> when (code()) {
                401 -> "Sesión de Battle.net expirada o sin permiso (401)."
                404 -> "Personaje no encontrado (404)."
                429 -> "Límite de peticiones de la API (429). Reintento automático."
                else -> "Error de la API de Blizzard (HTTP ${code()})."
            }
            else -> message ?: "Fallo de red desconocido."
        },
    )
}
