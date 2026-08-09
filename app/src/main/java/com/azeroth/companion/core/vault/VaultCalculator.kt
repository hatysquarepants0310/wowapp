package com.azeroth.companion.core.vault

import com.azeroth.companion.core.catalog.EconomyRules
import com.azeroth.companion.core.catalog.VaultRules
import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.GreatVaultProgress
import com.azeroth.companion.core.model.SlotProgress

/**
 * Lógica pura de Gran Bóveda y calculadora de mejora (§7.3), sin dependencias
 * de Android para poder testearse en JVM. La API no expone la Bóveda: todo lo
 * que sale de aquí es ESTIMADO por definición (§2.4).
 */
object VaultCalculator {

    fun slot(current: Int, thresholds: List<Int>, ilvls: List<Int>): SlotProgress = SlotProgress(
        current = current.coerceAtLeast(0),
        thresholds = thresholds,
        predictedRewardIlvl = thresholds.mapIndexed { i, threshold ->
            if (current >= threshold) ilvls.getOrNull(i) else null
        },
    )

    fun vault(
        characterId: Long,
        raidKillsThisWeek: Int,
        mythicPlusRunsThisWeek: Int,
        worldActivitiesThisWeek: Int,
        rules: VaultRules,
    ): GreatVaultProgress = GreatVaultProgress(
        characterId = characterId,
        raidSlots = slot(raidKillsThisWeek, rules.raidThresholds, rules.raidSlotIlvl),
        mythicPlusSlots = slot(mythicPlusRunsThisWeek, rules.mythicPlusThresholds, rules.mythicPlusSlotIlvl),
        worldSlots = slot(worldActivitiesThisWeek, rules.worldThresholds, rules.worldSlotIlvl),
        confidence = Confidence.ESTIMATED,
    )

    /**
     * Casilla calculada como en el juego: se ordenan los logros de la semana de
     * mejor a peor y la casilla del umbral N premia al nivel del N-ésimo. Así, 4
     * mazmorras +10 y una +2 no dan tres recompensas de +10: la primera casilla
     * premia tu mejor llave, la segunda tu 4.ª y la tercera tu 8.ª.
     */
    fun slotFromTiers(tiers: List<Int>, thresholds: List<Int>): SlotProgress {
        val sorted = tiers.sortedDescending()
        return SlotProgress(
            current = sorted.size,
            thresholds = thresholds,
            predictedRewardIlvl = thresholds.map { t ->
                if (sorted.size >= t) sorted[t - 1] else null
            },
        )
    }

    /**
     * Gran Bóveda a partir de los datos exactos del perfil: la dificultad de
     * cada jefe de banda matado esta semana y el nivel de cada llave M+ de la
     * semana. Solo la fila de Mundo queda estimada (la API no expone la Bóveda
     * ni el progreso semanal de Delves; se deduce de la estadística acumulada).
     */
    fun vaultFromTiers(
        characterId: Long,
        raidIlvls: List<Int>,
        mythicIlvls: List<Int>,
        worldActivitiesThisWeek: Int,
        rules: VaultRules,
    ): GreatVaultProgress = GreatVaultProgress(
        characterId = characterId,
        raidSlots = slotFromTiers(raidIlvls, rules.raidThresholds),
        mythicPlusSlots = slotFromTiers(mythicIlvls, rules.mythicPlusThresholds),
        worldSlots = slot(worldActivitiesThisWeek, rules.worldThresholds, rules.worldSlotIlvl),
        confidence = Confidence.ESTIMATED,
    )

    /**
     * Nivel de recompensa de cada JEFE distinto matado esta semana.
     *
     * La Bóveda cuenta cada jefe una sola vez por semana aunque se mate en
     * varias dificultades, y premia por la mejor de ellas. Antes se contaba
     * cada par jefe+dificultad, así que limpiar la banda en normal y luego en
     * heroico daba el doble de jefes y desbloqueaba casillas que el juego no
     * daba.
     *
     * [kills] son pares (nombre del jefe, ilvl de esa dificultad).
     */
    fun raidTiersByBoss(kills: List<Pair<String, Int>>): List<Int> =
        kills.groupBy { it.first }.map { (_, group) -> group.maxOf { it.second } }

    data class UpgradePlan(
        val stepsAffordable: Int,
        val ilvlGain: Int,
        val crestsLeft: Int,
        val crestsToNextStep: Int,
    )

    /** Cuántas mejoras alcanzan los crests disponibles y qué ilvl ganan. */
    fun upgradePlan(crestsAvailable: Int, rules: EconomyRules): UpgradePlan {
        val cost = rules.crestCostPerUpgrade.coerceAtLeast(1)
        val steps = (crestsAvailable / cost).coerceAtLeast(0)
        val left = crestsAvailable - steps * cost
        return UpgradePlan(
            stepsAffordable = steps,
            ilvlGain = steps * rules.ilvlPerUpgrade,
            crestsLeft = left,
            crestsToNextStep = cost - left,
        )
    }
}
