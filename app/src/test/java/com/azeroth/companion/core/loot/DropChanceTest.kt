package com.azeroth.companion.core.loot

import com.azeroth.companion.core.catalog.LootRules
import com.azeroth.companion.core.model.Confidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DropChanceTest {

    private val rules = LootRules()

    @Test
    fun `objeto de banda se estima con el tamaño de la tabla`() {
        val chance = DropChanceCalculator.forBossItem(
            itemId = 1, tableSize = 16, isMount = false,
            isRaid = true, isFinalBoss = true, rules = rules,
        )
        // 4 objetos por muerte entre 16 de la tabla = 25 %.
        assertEquals(25.0, DropChanceCalculator.percent(chance)!!, 0.001)
        assertEquals(Confidence.ESTIMATED, chance.confidence)
    }

    /**
     * Las monturas NO salen de la tabla del jefe: son una tirada aparte. Meterlas
     * en la división daría ~8 % en vez del ~1 % real.
     */
    @Test
    fun `una montura no se estima con la tabla del jefe`() {
        val chance = DropChanceCalculator.forBossItem(
            itemId = 1, tableSize = 12, isMount = true,
            isRaid = false, isFinalBoss = true, rules = rules,
        )
        assertTrue(chance is DropChance.RareRoll)
        assertEquals(rules.mountDropPercentEstimate, DropChanceCalculator.percent(chance)!!, 0.001)
    }

    @Test
    fun `una tasa medida por la comunidad gana a la estimación`() {
        val chance = DropChanceCalculator.forBossItem(
            itemId = 42, tableSize = 10, isMount = true, isRaid = true,
            isFinalBoss = true, rules = rules.copy(knownDropRates = mapOf("42" to 0.7)),
        )
        assertEquals(0.7, DropChanceCalculator.percent(chance)!!, 0.001)
        assertEquals(Confidence.CONFIRMED, chance.confidence)
    }

    @Test
    fun `recompensa de misión con varias opciones es una elección`() {
        assertEquals(50.0, DropChanceCalculator.percent(DropChance.Choice(2))!!, 0.001)
        assertEquals(100.0, DropChanceCalculator.percent(DropChance.Guaranteed)!!, 0.001)
        assertNull(DropChanceCalculator.percent(DropChance.Unknown("sin datos")))
    }

    @Test
    fun `un jefe sin tabla publicada no inventa un número`() {
        val chance = DropChanceCalculator.forBossItem(
            itemId = 1, tableSize = 0, isMount = false,
            isRaid = true, isFinalBoss = false, rules = rules,
        )
        assertTrue(chance is DropChance.Unknown)
        assertNull(DropChanceCalculator.percent(chance))
    }
}
