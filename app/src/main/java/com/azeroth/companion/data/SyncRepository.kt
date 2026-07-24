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
    private val json: Json,
) {

    /** Importa la lista de personajes de la cuenta al roster local. */
    suspend fun syncRoster(): SyncResult {
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        return runCatching {
            val profile = api.userProfile(namespace = region.namespaceProfile)
            profile.wow_accounts.flatMap { it.characters }.forEach { dto ->
                characterDao.upsert(
                    CharacterEntity(
                        id = dto.id,
                        name = dto.name,
                        realmSlug = dto.realm.slug,
                        realmName = dto.realm.name ?: dto.realm.slug,
                        region = region.name,
                        faction = dto.faction?.type ?: "NEUTRAL",
                        playableClass = dto.playable_class?.name ?: "",
                        activeSpec = null,
                        level = dto.level,
                        averageItemLevel = 0,
                        equippedItemLevel = 0,
                        isMain = false,
                        lastLogin = null,
                        lastSyncedAt = null,
                    ),
                )
            }
            SyncResult.Success
        }.getOrElse { it.toSyncFailure() }
    }

    /** Sync completo del personaje activo: perfil + snapshot + detección. */
    suspend fun syncActiveCharacter(): SyncResult {
        val settings = settingsRepository.settings.first()
        val region = settings.region
        val character = activeCharacter(settings.activeCharacterId)
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
                ),
            )

            val runsThisWeek = mythic?.current_period?.best_runs
                ?.count { it.completed_timestamp >= lastReset.toEpochMilli() } ?: 0
            val raidKills = raids?.expansions?.flatMap { it.instances }
                ?.associate { it.instance.id to it.modes.sumOf { m -> m.progress?.completed_count ?: 0 } }
                ?: emptyMap()

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

    /** Evalúa cada regla de detección y persiste resultados (sin pisar overrides). */
    private suspend fun runDetection(characterId: Long, lastReset: Instant) {
        val baseline = snapshotDao.firstAfter(characterId, lastReset)?.toView()
        val current = snapshotDao.latest(characterId)?.toView()
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

    private suspend fun activeCharacter(activeId: Long?): CharacterEntity? {
        val all = characterDao.observeAll().first()
        return all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
    }

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
