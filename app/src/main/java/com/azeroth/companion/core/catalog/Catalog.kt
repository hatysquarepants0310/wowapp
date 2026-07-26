package com.azeroth.companion.core.catalog

import com.azeroth.companion.core.model.SeasonalReward
import com.azeroth.companion.core.model.TrackedTask
import com.azeroth.companion.core.model.WorldEventDefinition
import com.azeroth.companion.core.time.ResetRules
import kotlinx.serialization.Serializable

/**
 * Catálogo de contenido versionado (§7). Todo el contenido específico de la
 * expansión (cadencias, IDs de quest, fechas) vive aquí, nunca en el código:
 * cambia por parche y debe poder actualizarse sin publicar un APK.
 */
@Serializable
data class Catalog(
    val catalogVersion: Int,
    val gameVersion: String,
    val updatedAt: String,
    val resets: List<ResetRules>,
    val worldEvents: List<WorldEventDefinition>,
    val weeklyTasks: List<TrackedTask>,
    val seasonalRewards: List<SeasonalReward>,
    val zones: List<ZoneInfo> = emptyList(),
    val vault: VaultRules = VaultRules(),
    val loot: LootRules = LootRules(),
    val economy: EconomyRules = EconomyRules(),
    val season: SeasonInfo = SeasonInfo(),
    /** ID de expansión en el journal de Blizzard para listar mazmorras y bandas. */
    val journalExpansionId: Int = 516,
)

/** Temporada en curso. La fecha de cierre es SIEMPRE estimada (§8.3). */
@Serializable
data class SeasonInfo(
    val id: String = "midnight_s1",
    val name: Map<String, String> = mapOf("es_MX" to "Midnight Temporada 1"),
    /** Estimación; Blizzard rara vez confirma la fecha con antelación. */
    val endEstimateUtc: String? = null,
)

@Serializable
data class ZoneInfo(
    val id: String,
    val name: Map<String, String>,
)

/** Umbrales de la Gran Bóveda e ilvl previsto por slot (§7.3). Datos de parche → catálogo. */
@Serializable
data class VaultRules(
    val raidThresholds: List<Int> = listOf(2, 4, 6),
    val mythicPlusThresholds: List<Int> = listOf(1, 4, 8),
    val worldThresholds: List<Int> = listOf(2, 4, 8),
    val raidSlotIlvl: List<Int> = listOf(619, 622, 626),
    val mythicPlusSlotIlvl: List<Int> = listOf(619, 623, 626),
    val worldSlotIlvl: List<Int> = listOf(610, 616, 619),
    /** IDs de tareas del catálogo que aportan al slot de mundo. */
    val worldContributingTaskIds: List<String> = listOf(
        "delves_bountiful", "prey_eversong", "prey_zulaman", "prey_harandar",
        "prey_voidstorm", "weekly_world_boss",
    ),
    /**
     * ilvl de la recompensa según la DIFICULTAD del jefe, igual que en el juego:
     * la casilla N premia al nivel del jefe N-ésimo mejor de la semana, no un
     * valor fijo por posición.
     */
    val raidIlvlByDifficulty: Map<String, Int> = mapOf(
        "LFR" to 606, "NORMAL" to 619, "HEROIC" to 632, "MYTHIC" to 645,
    ),
    /** ilvl de la recompensa según el nivel de la llave M+ (nivel → ilvl). */
    val mythicPlusIlvlByLevel: Map<String, Int> = mapOf(
        "2" to 619, "3" to 623, "4" to 626, "5" to 626, "6" to 629,
        "7" to 632, "8" to 636, "9" to 639, "10" to 642,
    ),
    /** Estadística del perfil que cuenta Delves completadas (fila de Mundo). */
    val delveStatisticId: Int = 40734,
)

/**
 * Reglas para ESTIMAR la probabilidad de botín. Blizzard no publica tasas de
 * caída por objeto: lo único público es la tabla de botín de cada jefe (journal).
 * Con el tamaño de la tabla y cuántos objetos suelta un intento se obtiene una
 * estimación honesta, que la app muestra siempre marcada como estimada.
 */
@Serializable
data class LootRules(
    /** Objetos que suelta un jefe de banda por muerte (grupo de 20). */
    val raidItemsPerKill: Double = 4.0,
    /** Objetos que suelta un jefe de mazmorra por muerte. */
    val dungeonItemsPerKill: Double = 1.0,
    /** Objetos extra del último jefe de una mazmorra. */
    val dungeonFinalBossBonusItems: Double = 1.0,
    /**
     * Orden de magnitud de las monturas de botín: NO sale de la tabla del jefe,
     * es una tirada aparte que Blizzard no publica. Se deja aquí para que la
     * comunidad lo corrija con observaciones reales.
     */
    val mountDropPercentEstimate: Double = 1.0,
    /**
     * Tasas medidas por la comunidad, objeto → porcentaje. Tienen prioridad
     * sobre cualquier estimación. Vacío por defecto: preferimos decir "estimado"
     * antes que inventar una cifra.
     */
    val knownDropRates: Map<String, Double> = emptyMap(),
)

/** Costos de mejora de equipo (§7.3): calculadora de crests. */
@Serializable
data class EconomyRules(
    val crestName: Map<String, String> = mapOf("es_MX" to "Dawncrests", "en_US" to "Dawncrests"),
    val crestCostPerUpgrade: Int = 15,
    val ilvlPerUpgrade: Int = 3,
    val weeklyCrestCap: Int = 90,
    val maxUpgradeStepsPerItem: Int = 6,
)
