package com.azeroth.companion.core.detection

import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.DetectionRule
import com.azeroth.companion.core.model.WeeklyActivityKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectionEngineTest {

    private val engine = DetectionEngine()

    private fun snapshot(
        quests: Set<Int> = emptySet(),
        reps: Map<Int, Int> = emptyMap(),
        runs: Int = 0,
        before: Set<Int>? = emptySet(),
    ) = SnapshotView(
        completedQuestIds = quests,
        reputations = reps,
        mythicPlusRunsThisWeek = runs,
        raidKills = emptyMap(),
        questsBeforeReset = before,
    )

    @Test
    fun `quest completed this week is CONFIRMED`() {
        val result = engine.evaluate(
            DetectionRule.QuestCompleted(listOf(84001)),
            baseline = snapshot(),
            current = snapshot(quests = setOf(84001), before = emptySet()),
        )
        assertEquals(1, result.completions)
        assertEquals(Confidence.CONFIRMED, result.confidence)
    }

    /**
     * Midnight no limpia /quests/completed al reset. Si la semanal ya estaba
     * en el snapshot anterior, no es de esta semana aunque siga en completed.
     */
    @Test
    fun `weekly quest still in completed from last week does not count`() {
        val result = engine.evaluate(
            DetectionRule.QuestCompleted(listOf(84001)),
            baseline = snapshot(quests = setOf(84001)),
            current = snapshot(quests = setOf(84001), before = setOf(84001)),
        )
        assertEquals(0, result.completions)
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
        assertEquals(Confidence.ESTIMATED, result.confidence)
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

    /**
     * La detección que sustituyó a los IDs de misión inventados. Los marcadores
     * "Midnight: X" no aparecen en /quests/completed (comprobado sobre 75
     * personajes), así que las semanales se apoyan en actividad medida.
     */
    @Test
    fun `la actividad medida marca la semanal`() {
        val engine = DetectionEngine()
        val view = SnapshotView(
            completedQuestIds = emptySet(),
            reputations = emptyMap(),
            mythicPlusRunsThisWeek = 3,
            raidKills = emptyMap(),
            raidBossKillsThisWeek = 2,
            delvesThisWeek = 0,
            repeatableQuestsDoneThisWeek = 5,
        )
        fun rule(kind: WeeklyActivityKind) = DetectionRule.ActivityThisWeek(kind, min = 1)
        assertEquals(3, engine.evaluate(rule(WeeklyActivityKind.MYTHIC_PLUS), null, view).completions)
        assertEquals(2, engine.evaluate(rule(WeeklyActivityKind.RAID_BOSS), null, view).completions)
        assertEquals(5, engine.evaluate(rule(WeeklyActivityKind.REPEATABLE_QUEST), null, view).completions)
        // Sin Delves esta semana la fila no se marca, y no se inventa un valor.
        assertEquals(0, engine.evaluate(rule(WeeklyActivityKind.DELVE), null, view).completions)
        assertEquals(
            Confidence.PREDICTED,
            engine.evaluate(rule(WeeklyActivityKind.DELVE), null, view).confidence,
        )
    }

    /** Sin snapshot no se marca nada: la ausencia de datos no es un cero real. */
    @Test
    fun `sin snapshot la actividad no marca`() {
        val result = DetectionEngine().evaluate(
            DetectionRule.ActivityThisWeek(WeeklyActivityKind.RAID_BOSS, min = 1), null, null,
        )
        assertEquals(0, result.completions)
    }
}
