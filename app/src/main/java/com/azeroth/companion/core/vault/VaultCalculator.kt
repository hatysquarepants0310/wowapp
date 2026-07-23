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
