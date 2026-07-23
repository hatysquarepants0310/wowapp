package com.azeroth.companion.core.time

import com.azeroth.companion.core.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant

class ResetClockTest {

    private val usRules = ResetRules(
        region = Region.US,
        realmZoneId = "America/Los_Angeles",
        weeklyResetDay = DayOfWeek.TUESDAY,
        weeklyResetTime = "15:00",
        dailyResetTime = "15:00",
        resetZoneId = "Z",
    )
    private val clock = ResetClock(usRules)

    @Test
    fun `next weekly reset from mid-week is following Tuesday 15h UTC`() {
        // Jueves 2026-07-23 12:00 UTC.
        val from = Instant.parse("2026-07-23T12:00:00Z")
        assertEquals(Instant.parse("2026-07-28T15:00:00Z"), clock.nextWeeklyReset(from))
    }

    @Test
    fun `last weekly reset from mid-week is previous Tuesday 15h UTC`() {
        val from = Instant.parse("2026-07-23T12:00:00Z")
        assertEquals(Instant.parse("2026-07-21T15:00:00Z"), clock.lastWeeklyReset(from))
    }

    @Test
    fun `exactly at reset time the reset counts as done`() {
        val at = Instant.parse("2026-07-21T15:00:00Z")
        assertEquals(at, clock.lastWeeklyReset(at))
        assertEquals(Instant.parse("2026-07-28T15:00:00Z"), clock.nextWeeklyReset(at))
    }

    @Test
    fun `daily reset rolls to next day when already past`() {
        val from = Instant.parse("2026-07-23T16:00:00Z")
        assertEquals(Instant.parse("2026-07-24T15:00:00Z"), clock.nextDailyReset(from))
    }

    @Test
    fun `game week membership`() {
        val now = Instant.parse("2026-07-23T12:00:00Z")
        assertTrue(clock.isInCurrentGameWeek(Instant.parse("2026-07-22T00:00:00Z"), now))
        assertTrue(!clock.isInCurrentGameWeek(Instant.parse("2026-07-20T00:00:00Z"), now))
    }

    @Test
    fun `eu reset differs from us reset`() {
        val eu = ResetClock(
            usRules.copy(region = Region.EU, realmZoneId = "Europe/Paris",
                weeklyResetDay = DayOfWeek.WEDNESDAY, weeklyResetTime = "04:00"),
        )
        val from = Instant.parse("2026-07-23T12:00:00Z")
        assertEquals(Instant.parse("2026-07-29T04:00:00Z"), eu.nextWeeklyReset(from))
    }
}
