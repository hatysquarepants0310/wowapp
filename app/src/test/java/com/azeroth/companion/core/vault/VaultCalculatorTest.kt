package com.azeroth.companion.core.vault

import com.azeroth.companion.core.catalog.EconomyRules
import com.azeroth.companion.core.catalog.VaultRules
import com.azeroth.companion.core.model.Confidence
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultCalculatorTest {

    private val rules = VaultRules()

    @Test
    fun `slot unlocks predicted ilvls as thresholds are met`() {
        val slot = VaultCalculator.slot(4, listOf(1, 4, 8), listOf(619, 623, 626))
        assertEquals(listOf<Int?>(619, 623, null), slot.predictedRewardIlvl)
        assertEquals(4, slot.current)
    }

    @Test
    fun `vault is always ESTIMATED because the API does not expose it`() {
        val vault = VaultCalculator.vault(1L, 2, 8, 3, rules)
        assertEquals(Confidence.ESTIMATED, vault.confidence)
        assertEquals(2, vault.raidSlots.current)
        assertEquals(8, vault.mythicPlusSlots.current)
        assertEquals(3, vault.worldSlots.current)
        // 8 runs de M+ desbloquean los 3 slots.
        assertEquals(0, vault.mythicPlusSlots.predictedRewardIlvl.count { it == null })
    }

    @Test
    fun `negative inputs clamp to zero`() {
        val vault = VaultCalculator.vault(1L, -3, 0, 0, rules)
        assertEquals(0, vault.raidSlots.current)
    }

    @Test
    fun `upgrade plan math`() {
        val economy = EconomyRules(crestCostPerUpgrade = 15, ilvlPerUpgrade = 3)
        val plan = VaultCalculator.upgradePlan(47, economy)
        assertEquals(3, plan.stepsAffordable)
        assertEquals(9, plan.ilvlGain)
        assertEquals(2, plan.crestsLeft)
        assertEquals(13, plan.crestsToNextStep)
    }

    @Test
    fun `zero crests affords nothing`() {
        val plan = VaultCalculator.upgradePlan(0, EconomyRules())
        assertEquals(0, plan.stepsAffordable)
        assertEquals(0, plan.ilvlGain)
    }

    /**
     * Igual que en el juego: la casilla del umbral N premia al nivel de la
     * N-ésima mejor actividad, no un ilvl fijo por posición. Con 4 llaves +10 y
     * una +2, la primera casilla vale +10 y la del umbral 4 también, pero la de
     * 8 sigue bloqueada.
     */
    @Test
    fun `slot from tiers rewards the Nth best activity`() {
        val slot = VaultCalculator.slotFromTiers(
            tiers = listOf(642, 642, 642, 642, 619),
            thresholds = listOf(1, 4, 8),
        )
        assertEquals(5, slot.current)
        assertEquals(listOf<Int?>(642, 642, null), slot.predictedRewardIlvl)
    }

    @Test
    fun `raid slots use the difficulty of each kill`() {
        val vault = VaultCalculator.vaultFromTiers(
            characterId = 1L,
            raidIlvls = listOf(645, 632, 632, 619),
            mythicIlvls = emptyList(),
            worldActivitiesThisWeek = 0,
            rules = rules,
        )
        assertEquals(4, vault.raidSlots.current)
        // Umbral 2 -> 2.º mejor (632); umbral 4 -> 4.º mejor (619); umbral 6 bloqueado.
        assertEquals(listOf<Int?>(632, 619, null), vault.raidSlots.predictedRewardIlvl)
        assertEquals(0, vault.mythicPlusSlots.current)
    }
}
