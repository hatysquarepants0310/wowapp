package com.azeroth.companion.core.detection

import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.WeekTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qué cuenta como "esta semana" cuando Blizzard no fecha la misión.
 *
 * El fallo que el jugador ve: una semanal hecha hace DOS semanas sigue
 * marcada, y las llaves salen 0 en silencio si el perfil no se ha
 * actualizado desde el reset.
 */
class ThisWeekTest {

    @Test
    fun `semanal en completed desde hace dos semanas no está hecha esta semana`() {
        val done = ThisWeek.questIdsDone(
            candidates = listOf(84001),
            completedNow = setOf(84001),
            completedBeforeReset = setOf(84001),
        )
        assertTrue(done.isEmpty())
        assertEquals(Confidence.ESTIMATED, ThisWeek.questConfidence(setOf(84001), done))
    }

    @Test
    fun `semanal que aparece después del reset sí está hecha esta semana`() {
        val done = ThisWeek.questIdsDone(
            candidates = listOf(84001, 84002),
            completedNow = setOf(84001),
            completedBeforeReset = emptySet(),
        )
        assertEquals(setOf(84001), done)
        assertEquals(Confidence.CONFIRMED, ThisWeek.questConfidence(emptySet(), done))
    }

    @Test
    fun `sin snapshot anterior al reset no se afirma la semanal`() {
        val done = ThisWeek.questIdsDone(
            candidates = listOf(84001),
            completedNow = setOf(84001),
            completedBeforeReset = null,
        )
        assertTrue(done.isEmpty())
        assertEquals(Confidence.PREDICTED, ThisWeek.questConfidence(null, done))
    }

    @Test
    fun `periodo M+ desfasado no cuenta llaves y marca perfil desfasado`() {
        val slice = ThisWeek.mythicKeys(
            livePeriodId = 1200,
            profilePeriodId = 1199,
            lastLoginMillis = 2_000L,
            lastResetMillis = 1_000L,
            profileRunCount = 8,
            runTimestamps = listOf(500L, 800L),
        )
        assertEquals(0, slice.count)
        assertTrue(slice.stale)
        assertEquals(WeekTrust.STALE, slice.trust)
    }

    @Test
    fun `periodo M+ vigente cuenta todas las llaves del periodo`() {
        val slice = ThisWeek.mythicKeys(
            livePeriodId = 1200,
            profilePeriodId = 1200,
            lastLoginMillis = 2_000L,
            lastResetMillis = 1_000L,
            profileRunCount = 3,
            runTimestamps = listOf(1_100L, 1_200L, 1_300L),
        )
        assertEquals(3, slice.count)
        assertFalse(slice.stale)
        assertEquals(WeekTrust.CONFIRMED, slice.trust)
    }

    @Test
    fun `login anterior al reset deja el perfil desfasado aunque el periodo coincida`() {
        val slice = ThisWeek.mythicKeys(
            livePeriodId = 1200,
            profilePeriodId = 1200,
            lastLoginMillis = 500L,
            lastResetMillis = 1_000L,
            profileRunCount = 4,
            runTimestamps = emptyList(),
        )
        assertEquals(0, slice.count)
        assertTrue(slice.stale)
        assertEquals(WeekTrust.STALE, slice.trust)
    }

    @Test
    fun `sin ids de periodo se filtran llaves por fecha de cierre`() {
        val slice = ThisWeek.mythicKeys(
            livePeriodId = null,
            profilePeriodId = null,
            lastLoginMillis = 2_000L,
            lastResetMillis = 1_000L,
            profileRunCount = 3,
            runTimestamps = listOf(400L, 1_100L, 1_500L),
        )
        assertEquals(2, slice.count)
        assertFalse(slice.stale)
        assertEquals(WeekTrust.CONFIRMED, slice.trust)
    }

    @Test
    fun `jefe con last_kill anterior al reset no cuenta esta semana`() {
        assertEquals(
            1,
            ThisWeek.raidKillsThisWeek(
                lastKillTimestamps = listOf(500L, 1_500L, 0L),
                lastResetMillis = 1_000L,
            ),
        )
    }

    @Test
    fun `un 0 de llaves con perfil desfasado no es un 0 real`() {
        assertEquals("—", ThisWeek.countLabel(0, stale = true))
        assertEquals("0", ThisWeek.countLabel(0, stale = false))
        assertEquals("3", ThisWeek.countLabel(3, stale = false))
    }

    @Test
    fun `la confianza de la semana distingue confirmado estimado y desfasado`() {
        assertEquals(
            WeekTrust.STALE,
            ThisWeek.trust(profileStale = true, hasBaseline = true, snapshotBeforeReset = false),
        )
        assertEquals(
            WeekTrust.CONFIRMED,
            ThisWeek.trust(profileStale = false, hasBaseline = true, snapshotBeforeReset = false),
        )
        assertEquals(
            WeekTrust.ESTIMATED,
            ThisWeek.trust(profileStale = false, hasBaseline = false, snapshotBeforeReset = false),
        )
    }
}
