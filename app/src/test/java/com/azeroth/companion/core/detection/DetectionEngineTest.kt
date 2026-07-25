package com.azeroth.companion.core.detection

import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.DetectionRule
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectionEngineTest {

    private val engine = DetectionEngine()

    private fun snapshot(quests: Set<Int> = emptySet(), reps: Map<Int, Int> = emptyMap(), runs: Int = 0) =
        SnapshotView(completedQuestIds = quests, reputations = reps, mythicPlusRunsThisWeek = runs, raidKills = emptyMap())

    @Test
    fun `quest completed this week is ESTIMATED`() {
        val result = engine.evaluate(
            DetectionRule.QuestCompleted(listOf(84001)),
            baseline = snapshot(),
            current = snapshot(quests = setOf(84001)),
        )
        assertEquals(1, result.completions)
        assertEquals(Confidence.ESTIMATED, result.confidence)
    }

    /**
     * Blizzard borra la marca de completada de las misiones repetibles en cada
     * reset semanal, así que la presencia en /quests/completed YA significa
     * "hecha esta semana" aunque la línea base también la tuviera (el caso
     * normal: el primer sync de la semana es posterior a haberla hecho).
     */
    @Test
    fun `weekly quest present in the profile counts even without a fresh baseline`() {
        val result = engine.evaluate(
            DetectionRule.QuestCompleted(listOf(84001)),
            baseline = snapshot(quests = setOf(84001)),
            current = snapshot(quests = setOf(84001)),
        )
        assertEquals(1, result.completions)
        assertEquals(Confidence.ESTIMATED, result.confidence)
    }

    @Test
    fun `quest absent from the profile does not count`() {
        val result = engine.evaluate(
            DetectionRule.QuestCompleted(listOf(84001)),
            baseline = snapshot(quests = setOf(84001)),
            current = snapshot(),
        )
        assertEquals(0, result.completions)
        assertEquals(Confidence.PREDICTED, result.confidence)
    }

    @Test
    fun `reputation gain counts completions by threshold`() {
        val result = engine.evaluate(
            DetectionRule.ReputationGain(factionId = 2701, minDelta = 2000),
            baseline = snapshot(reps = mapOf(2701 to 1000)),
            current = snapshot(reps = mapOf(2701 to 5100)),
        )
        assertEquals(2, result.completions)
        assertEquals(Confidence.ESTIMATED, result.confidence)
    }

    @Test
    fun `manual only never auto-detects`() {
        val result = engine.evaluate(DetectionRule.ManualOnly, snapshot(), snapshot(quests = setOf(1)))
        assertEquals(0, result.completions)
    }

    @Test
    fun `anyOf takes the best branch`() {
        val rule = DetectionRule.AnyOf(
            listOf(
                DetectionRule.ManualOnly,
                DetectionRule.MythicPlusRuns(minRuns = 1),
            ),
        )
        val result = engine.evaluate(rule, snapshot(), snapshot(runs = 4))
        assertEquals(4, result.completions)
        assertEquals(Confidence.ESTIMATED, result.confidence)
    }

    @Test
    fun `allOf requires every branch`() {
        val rule = DetectionRule.AllOf(
            listOf(
                DetectionRule.QuestCompleted(listOf(84001)),
                DetectionRule.MythicPlusRuns(minRuns = 1),
            ),
        )
        val partial = engine.evaluate(rule, snapshot(), snapshot(quests = setOf(84001)))
        assertEquals(0, partial.completions)
        val full = engine.evaluate(rule, snapshot(), snapshot(quests = setOf(84001), runs = 2))
        assertEquals(1, full.completions)
    }
}
