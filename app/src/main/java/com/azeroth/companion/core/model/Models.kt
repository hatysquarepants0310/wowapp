package com.azeroth.companion.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

// ---------- Región y cuenta ----------

enum class Region(val apiHost: String, val oauthHost: String) {
    US("https://us.api.blizzard.com", "https://oauth.battle.net"),
    EU("https://eu.api.blizzard.com", "https://oauth.battle.net"),
    KR("https://kr.api.blizzard.com", "https://oauth.battle.net"),
    TW("https://tw.api.blizzard.com", "https://oauth.battle.net");

    val namespaceProfile get() = "profile-${name.lowercase()}"
    val namespaceStatic get() = "static-${name.lowercase()}"
    val namespaceDynamic get() = "dynamic-${name.lowercase()}"
}

enum class Faction { ALLIANCE, HORDE, NEUTRAL }

data class Character(
    val id: Long,
    val name: String,
    val realmSlug: String,
    val realmName: String,
    val region: Region,
    val faction: Faction,
    val playableClass: String,
    val activeSpec: String?,
    val level: Int,
    val averageItemLevel: Int,
    val equippedItemLevel: Int,
    val isMain: Boolean,
    val lastLogin: Instant?,
    val lastSyncedAt: Instant?,
)

// ---------- Confianza del dato ----------

@Serializable
enum class Confidence { CONFIRMED, ESTIMATED, PREDICTED }

// ---------- Cadencias de eventos ----------

@Serializable
sealed interface EventCadence {
    /** Cada N minutos desde una época conocida, alineado a hora del reino. */
    @Serializable
    @SerialName("FixedInterval")
    data class FixedInterval(
        val intervalMinutes: Int,
        @Serializable(with = InstantSerializer::class) val anchorUtc: Instant,
        val offsetMinutes: Int = 0,
    ) : EventCadence

    /** Días concretos de la semana a horas concretas (hora del reino). */
    @Serializable
    @SerialName("WeeklySchedule")
    data class WeeklySchedule(val entries: List<DayTime>) : EventCadence

    /** Ventanas de rotación de world quests (p. ej. miércoles y sábado). */
    @Serializable
    @SerialName("RefreshWindows")
    data class RefreshWindows(
        val daysOfWeek: List<DayOfWeek>,
        @Serializable(with = LocalTimeSerializer::class) val timeOfDay: LocalTime,
    ) : EventCadence

    @Serializable
    @SerialName("Continuous")
    data class Continuous(val minDurationHours: Int, val maxDurationHours: Int) : EventCadence
}

@Serializable
data class DayTime(
    val dayOfWeek: DayOfWeek,
    @Serializable(with = LocalTimeSerializer::class) val time: LocalTime,
)

// ---------- Eventos de mundo ----------

@Serializable
data class Coordinates(val x: Double, val y: Double)

@Serializable
data class EventPhase(
    val order: Int,
    val name: Map<String, String>,
    val durationSeconds: Int? = null,
    val playerActionHint: Map<String, String> = emptyMap(),
)

@Serializable
data class PreconditionHint(val text: Map<String, String>)

@Serializable
data class WorldEventDefinition(
    val id: String,
    val name: Map<String, String>,
    val zone: String,
    val location: Map<String, String> = emptyMap(),
    val coordinates: Coordinates? = null,
    val cadence: EventCadence,
    val phases: List<EventPhase> = emptyList(),
    val requiresLevel: Int = 0,
    val requiresCampaignComplete: Boolean = false,
    val associatedQuestIds: List<Int> = emptyList(),
    val weeklyRewardItemIds: List<Int> = emptyList(),
    val maxWeeklyCompletions: Int = 1,
    val requiresPresenceFromStart: Boolean = false,
    val forbidsRaidGroup: Boolean = false,
    val mountDropItemIds: List<Int> = emptyList(),
    val preconditions: List<PreconditionHint> = emptyList(),
    val knownIssues: List<Map<String, String>> = emptyList(),
    val defaultConfidence: Confidence = Confidence.PREDICTED,
)

data class EventOccurrence(
    val definitionId: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val confidence: Confidence,
)

// ---------- Tareas periódicas ----------

@Serializable
enum class ResetPeriod { DAILY, WEEKLY, BIWEEKLY_WQ, HALF_WEEKLY, SEASONAL, ONE_TIME }

@Serializable
enum class TaskCategory {
    WORLD_EVENT, WEEKLY_QUEST, GREAT_VAULT, DELVE, PREY_HUNT,
    OMNIUM_FOLIO, CURRENCY, PROFESSION, WORLD_BOSS, PVP,
    CAMPAIGN, SEASONAL_REWARD, LEGACY, CUSTOM
}

@Serializable
data class RewardHint(val text: Map<String, String>)

@Serializable
data class TrackedTask(
    val id: String,
    val category: TaskCategory,
    val title: Map<String, String>,
    val description: Map<String, String> = emptyMap(),
    val resetPeriod: ResetPeriod,
    val maxCompletions: Int = 1,
    val detectionRule: DetectionRule = DetectionRule.ManualOnly,
    val rewards: List<RewardHint> = emptyList(),
    val zone: String? = null,
    val minLevel: Int = 0,
    val isRemovedAtSeasonEnd: Boolean = false,
    val legacyExpansion: String? = null,
    val priorityWeight: Int = 0,
)

data class TaskState(
    val taskId: String,
    val characterId: Long,
    val completions: Int,
    val confidence: Confidence,
    val manualOverride: Boolean,
    val updatedAt: Instant,
)

// ---------- Reglas de detección ----------

@Serializable
sealed interface DetectionRule {
    @Serializable
    @SerialName("QuestCompleted")
    data class QuestCompleted(val questIds: List<Int>, val countsAs: Int = 1) : DetectionRule

    @Serializable
    @SerialName("QuestDelta")
    data class QuestDelta(val questIds: List<Int>) : DetectionRule

    @Serializable
    @SerialName("ReputationGain")
    data class ReputationGain(val factionId: Int, val minDelta: Int) : DetectionRule

    @Serializable
    @SerialName("AchievementCriteria")
    data class AchievementCriteria(val achievementId: Int, val criteriaIndex: Int) : DetectionRule

    @Serializable
    @SerialName("MythicPlusRuns")
    data class MythicPlusRuns(val minRuns: Int) : DetectionRule

    @Serializable
    @SerialName("RaidBossKills")
    data class RaidBossKills(val instanceId: Int, val minKills: Int) : DetectionRule

    @Serializable
    @SerialName("CurrencyThreshold")
    data class CurrencyThreshold(val currencyId: Int, val amount: Int) : DetectionRule

    @Serializable
    @SerialName("ManualOnly")
    data object ManualOnly : DetectionRule

    @Serializable
    @SerialName("AnyOf")
    data class AnyOf(val rules: List<DetectionRule>) : DetectionRule

    @Serializable
    @SerialName("AllOf")
    data class AllOf(val rules: List<DetectionRule>) : DetectionRule
}

// ---------- Gran Bóveda ----------

data class SlotProgress(
    val current: Int,
    val thresholds: List<Int>,
    val predictedRewardIlvl: List<Int?>,
)

data class GreatVaultProgress(
    val characterId: Long,
    val raidSlots: SlotProgress,
    val mythicPlusSlots: SlotProgress,
    val worldSlots: SlotProgress,
    val confidence: Confidence,
)

// ---------- Progresión de expansión ----------

data class OmniumFolioState(
    val characterId: Long,
    val unlockedRows: Int,
    val totalRows: Int = 5,
    val selectedRunes: Map<Int, String> = emptyMap(),
    val catchUpAvailable: Boolean = false,
    val nextStepQuestId: Int? = null,
)

enum class PreyDifficulty { NORMAL, HARD, MYTHIC }

data class PreyHunt(
    val characterId: Long,
    val zone: String,
    val progressPercent: Int,
    val revealed: Boolean,
    val difficultyCompleted: Set<PreyDifficulty>,
)

// ---------- Contenido con fecha límite ----------

@Serializable
enum class SeasonalRewardType { MOUNT, TITLE, ACHIEVEMENT, TRANSMOG, FEAT }

@Serializable
enum class Difficulty { EASY, MODERATE, HARD, EXTREME }

@Serializable
data class SeasonalReward(
    val id: String,
    val name: Map<String, String>,
    val type: SeasonalRewardType,
    val source: Map<String, String> = emptyMap(),
    val estimatedDifficulty: Difficulty,
    /** Fecha estimada de retirada; null = "fin de la temporada actual" sin fecha anunciada. */
    @Serializable(with = InstantSerializer::class) val removedAtEstimate: Instant? = null,
    /** ilvl mínimo con el que el objetivo es realista en el tiempo restante. */
    val realisticForItemLevel: Int? = null,
    /** Rating M+ mínimo realista, si aplica. */
    val realisticForMythicRating: Int? = null,
    /** Cross-check automático (§8.2): logro que marca esta recompensa como obtenida. */
    val achievementId: Int? = null,
    /** Cross-check automático (§8.2): montura de colección que la marca como obtenida. */
    val mountId: Int? = null,
)

enum class Viability { ACHIEVABLE, TIGHT, UNREALISTIC }

// ---------- Estado de autenticación ----------

sealed interface AuthState {
    data object LoggedOut : AuthState
    data class LoggedIn(val battleTag: String?) : AuthState
    /** OAuth roto: la app sigue viva en modo degradado. */
    data class Broken(val reason: String) : AuthState
}
