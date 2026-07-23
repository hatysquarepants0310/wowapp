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
)

@Serializable
data class ZoneInfo(
    val id: String,
    val name: Map<String, String>,
)
