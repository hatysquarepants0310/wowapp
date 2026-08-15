package com.azeroth.companion.core.detection

import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.DetectionRule
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Las dos formas de "hecho esta semana" que la API obliga a distinguir.
 *
 * Blizzard no dice si una misión es repetible, y las dos familias conviven en
 * la lista de semanales de Midnight:
 *
 *  - Repetible: la marca de completada desaparece en cada reset.
 *  - Rotatoria: cada semana es una misión distinta, que queda completada para
 *    siempre.
 *
 * Tratarlas igual producía el bug que el jugador veía: "Búsqueda de
 * conocimiento" se quedaba marcada el resto de la temporada después de hacerla
 * una sola vez.
 */
class RotatingQuestDetectionTest {

    private val engine = DetectionEngine()

    private fun view(
        completed: Set<Int>,
        before: Set<Int>? = null,
    ) = SnapshotView(
        completedQuestIds = completed,
        reputations = emptyMap(),
        mythicPlusRunsThisWeek = 0,
        raidKills = emptyMap(),
        questsBeforeReset = before,
    )

    @Test
    fun `repetible presente sin línea base cuenta como hecha`() {
        val rule = DetectionRule.QuestCompleted(listOf(100), repeatable = true)
        val result = engine.evaluate(rule, null, view(setOf(100)))
        assertEquals(1, result.completions)
        assertEquals(Confidence.ESTIMATED, result.confidence)
    }

    @Test
    fun `rotatoria sin línea base no se puede afirmar`() {
        val rule = DetectionRule.QuestCompleted(listOf(100), repeatable = false)
        val result = engine.evaluate(rule, null, view(setOf(100)))
        assertEquals(0, result.completions)
        assertEquals(Confidence.PREDICTED, result.confidence)
    }

    @Test
    fun `rotatoria con línea base y misión nueva es confirmada`() {
        val rule = DetectionRule.QuestCompleted(listOf(100), repeatable = false)
        val result = engine.evaluate(rule, null, view(setOf(100), before = setOf(99)))
        assertEquals(1, result.completions)
        assertEquals(Confidence.CONFIRMED, result.confidence)
    }

    /**
     * El caso que rompía la app: la misión ya estaba completada ANTES del reset,
     * o sea que es de una semana anterior y no debe marcar nada.
     */
    @Test
    fun `rotatoria ya completada antes del reset no marca`() {
        val rule = DetectionRule.QuestCompleted(listOf(100), repeatable = false)
        val result = engine.evaluate(rule, null, view(setOf(100), before = setOf(100)))
        assertEquals(0, result.completions)
    }

    @Test
    fun `repetible con línea base usa presencia actual`() {
        val rule = DetectionRule.QuestCompleted(listOf(100, 101), repeatable = true)
        val result = engine.evaluate(rule, null, view(setOf(100, 101), before = setOf(100)))
        assertEquals(2, result.completions)
        assertEquals(Confidence.CONFIRMED, result.confidence)
    }
}
