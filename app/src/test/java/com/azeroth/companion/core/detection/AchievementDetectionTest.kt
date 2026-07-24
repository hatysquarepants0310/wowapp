package com.azeroth.companion.core.detection

import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.DetectionRule
import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementDetectionTest {

    private val engine = DetectionEngine()
    private val rule = DetectionRule.AchievementCriteria(achievementId = 41000, criteriaIndex = 0)

    private fun snapshot(achievements: Set<Int>) = SnapshotView(
        completedQuestIds = emptySet(),
        reputations = emptyMap(),
        mythicPlusRunsThisWeek = 0,
        raidKills = emptyMap(),
        achievementIds = achievements,
    )

    @Test
    fun `achievement earned this week detects as ESTIMATED`() {
        val result = engine.evaluate(rule, snapshot(emptySet()), snapshot(setOf(41000)))
        assertEquals(1, result.completions)
        assertEquals(Confidence.ESTIMATED, result.confidence)
    }

    @Test
    fun `achievement earned before the reset does not re-count`() {
        val result = engine.evaluate(rule, snapshot(setOf(41000)), snapshot(setOf(41000)))
        assertEquals(0, result.completions)
    }

    @Test
    fun `missing achievement detects nothing`() {
        val result = engine.evaluate(rule, snapshot(emptySet()), snapshot(setOf(999)))
        assertEquals(0, result.completions)
        assertEquals(Confidence.PREDICTED, result.confidence)
    }
}
