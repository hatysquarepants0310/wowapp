package com.azeroth.companion.data

import com.azeroth.companion.core.model.Region
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Una mazmorra mítica completada. */
data class MythicRun(
    val dungeon: String,
    val level: Int,
    val score: Double?,
    val upgrades: Int,
    val clearTimeMs: Long?,
    val completedAt: Instant?,
)

/** Progreso en una banda, por dificultad. */
data class RaidProgress(
    val slug: String,
    val name: String,
    val totalBosses: Int,
    val normal: Int,
    val heroic: Int,
    val mythic: Int,
) {
    /** El resumen que la gente cita: la dificultad más alta con avance. */
    val summary: String
        get() = when {
            mythic > 0 -> "$mythic/$totalBosses M"
            heroic > 0 -> "$heroic/$totalBosses H"
            normal > 0 -> "$normal/$totalBosses N"
            else -> "0/$totalBosses"
        }
}

data class RaiderIoProfile(
    val score: Double,
    /** Color del tramo de puntuación, tal y como lo publica Raider.IO. */
    val scoreColor: String?,
    val bestRuns: List<MythicRun>,
    val weeklyRuns: List<MythicRun>,
    val raids: List<RaidProgress>,
    val profileUrl: String?,
    val crawledAt: Instant?,
)

/**
 * Puntuación de mítica+, mejores llaves y progreso de banda, desde Raider.IO.
 *
 * Blizzard publica las llaves de la temporada pero no una puntuación: el
 * "score" que la comunidad usa lo calcula Raider.IO, y su API pública es la
 * única forma honesta de mostrarlo. También da las llaves de ESTA semana ya
 * fechadas, que es más limpio que reconstruirlas desde el perfil.
 *
 * Ojo con lo que NO da: la Gran Bóveda. Probé `great_vault`, `weekly_rewards` y
 * `vault` como campos y los tres se ignoran. Nadie fuera del juego la expone.
 *
 * La respuesta se cachea en memoria: Raider.IO rastrea cada personaje cada
 * pocas horas, así que pedirla en cada entrada a la pantalla sería gastar su
 * cuota para recibir lo mismo.
 */
@Singleton
class RaiderIoRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {

    private data class Cached(val profile: RaiderIoProfile, val at: Instant)

    private val cache = mutableMapOf<String, Cached>()

    suspend fun profile(
        region: Region,
        realmSlug: String,
        characterName: String,
    ): Result<RaiderIoProfile> {
        val key = "${region.name}/$realmSlug/${characterName.lowercase()}"
        cache[key]?.takeIf { Duration.between(it.at, Instant.now()) < TTL }?.let {
            return Result.success(it.profile)
        }
        return runCatching {
            val dto = fetch(region, realmSlug, characterName)
            dto.toProfile().also { cache[key] = Cached(it, Instant.now()) }
        }
    }

    private suspend fun fetch(
        region: Region,
        realmSlug: String,
        characterName: String,
    ): ProfileDto = withContext(Dispatchers.IO) {
        val url = "https://raider.io/api/v1/characters/profile".toHttpUrl().newBuilder()
            .addQueryParameter("region", region.name.lowercase())
            .addQueryParameter("realm", realmSlug)
            .addQueryParameter("name", characterName)
            .addQueryParameter(
                "fields",
                "mythic_plus_scores_by_season:current,mythic_plus_best_runs," +
                    "mythic_plus_weekly_highest_level_runs,raid_progression",
            )
            .build()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            // 400 = el personaje no existe en Raider.IO todavía. No es un fallo
            // de la app y merece un mensaje distinto a "se cayó la red".
            if (response.code == 400) error(NOT_FOUND)
            if (!response.isSuccessful) error("HTTP ${response.code} en Raider.IO")
            val body = response.body?.string() ?: error("Respuesta vacía de Raider.IO")
            json.decodeFromString(ProfileDto.serializer(), body)
        }
    }

    // ---- DTOs: solo los campos que se pintan ------------------------------

    @Serializable
    private data class ProfileDto(
        val profile_url: String? = null,
        val last_crawled_at: String? = null,
        val mythic_plus_scores_by_season: List<SeasonScoreDto> = emptyList(),
        val mythic_plus_best_runs: List<RunDto> = emptyList(),
        val mythic_plus_weekly_highest_level_runs: List<RunDto> = emptyList(),
        val raid_progression: Map<String, RaidDto> = emptyMap(),
    ) {
        fun toProfile(): RaiderIoProfile {
            val season = mythic_plus_scores_by_season.firstOrNull()
            return RaiderIoProfile(
                score = season?.scores?.all ?: 0.0,
                scoreColor = season?.segments?.all?.color,
                bestRuns = mythic_plus_best_runs.map { it.toRun() },
                weeklyRuns = mythic_plus_weekly_highest_level_runs.map { it.toRun() },
                raids = raid_progression.map { (slug, raid) -> raid.toProgress(slug) }
                    // Las bandas grandes primero: una de un solo jefe no es la
                    // que define el progreso de nadie.
                    .sortedByDescending { it.totalBosses },
                profileUrl = profile_url,
                crawledAt = last_crawled_at?.let { runCatching { Instant.parse(it) }.getOrNull() },
            )
        }
    }

    @Serializable
    private data class SeasonScoreDto(
        val season: String = "",
        val scores: ScoresDto = ScoresDto(),
        val segments: SegmentsDto = SegmentsDto(),
    )

    @Serializable
    private data class ScoresDto(val all: Double = 0.0)

    @Serializable
    private data class SegmentsDto(val all: SegmentDto? = null)

    @Serializable
    private data class SegmentDto(val score: Double = 0.0, val color: String? = null)

    @Serializable
    private data class RunDto(
        val dungeon: String = "",
        @SerialName("mythic_level") val mythicLevel: Int = 0,
        val score: Double? = null,
        @SerialName("num_keystone_upgrades") val upgrades: Int = 0,
        @SerialName("clear_time_ms") val clearTimeMs: Long? = null,
        @SerialName("completed_at") val completedAt: String? = null,
    ) {
        fun toRun() = MythicRun(
            dungeon = dungeon,
            level = mythicLevel,
            score = score,
            upgrades = upgrades,
            clearTimeMs = clearTimeMs,
            completedAt = completedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        )
    }

    @Serializable
    private data class RaidDto(
        val total_bosses: Int = 0,
        val normal_bosses_killed: Int = 0,
        val heroic_bosses_killed: Int = 0,
        val mythic_bosses_killed: Int = 0,
    ) {
        fun toProgress(slug: String) = RaidProgress(
            slug = slug,
            // El slug es lo único que da la API; se convierte a algo legible.
            name = slug.split('-').joinToString(" ") { part ->
                part.replaceFirstChar { it.uppercase() }
            },
            totalBosses = total_bosses,
            normal = normal_bosses_killed,
            heroic = heroic_bosses_killed,
            mythic = mythic_bosses_killed,
        )
    }

    companion object {
        const val NOT_FOUND = "Raider.IO todavía no conoce a este personaje."
        private val TTL: Duration = Duration.ofHours(2)
    }
}
