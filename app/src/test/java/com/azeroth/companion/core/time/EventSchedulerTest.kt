package com.azeroth.companion.core.time

import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.DayTime
import com.azeroth.companion.core.model.EventCadence
import com.azeroth.companion.core.model.EventPhase
import com.azeroth.companion.core.model.WorldEventDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class EventSchedulerTest {

    private val zone = ZoneId.of("America/Los_Angeles")
    private val scheduler = DefaultEventScheduler(zone)
    private val anchor = Instant.parse("2026-07-21T15:00:00Z")

    private fun fixedIntervalEvent(intervalMinutes: Int = 30) = WorldEventDefinition(
        id = "stormarion_assault",
        name = mapOf("es_MX" to "Asalto a Stormarion"),
        zone = "voidstorm",
        cadence = EventCadence.FixedInterval(intervalMinutes, anchor),
        phases = listOf(
            EventPhase(1, mapOf("es_MX" to "Preparación"), durationSeconds = 300),
            EventPhase(2, mapOf("es_MX" to "Oleada 1"), durationSeconds = null),
        ),
    )

    @Test
    fun `next occurrence lands on the interval grid`() {
        val from = anchor.plus(Duration.ofMinutes(7))
        val occ = scheduler.nextOccurrence(fixedIntervalEvent(), from)
        assertNotNull(occ)
        assertEquals(anchor.plus(Duration.ofMinutes(30)), occ!!.startsAt)
    }

    @Test
    fun `query exactly at a start returns the following occurrence`() {
        val occ = scheduler.nextOccurrence(fixedIntervalEvent(), anchor)
        assertEquals(anchor.plus(Duration.ofMinutes(30)), occ!!.startsAt)
    }

    @Test
    fun `query before the anchor returns the anchor itself`() {
        val occ = scheduler.nextOccurrence(fixedIntervalEvent(), anchor.minusSeconds(3600))
        assertEquals(anchor, occ!!.startsAt)
    }

    @Test
    fun `occurrences in a 2h range with 30min cadence yields 4`() {
        val occs = scheduler.occurrencesInRange(
            fixedIntervalEvent(),
            anchor.plusSeconds(1)..anchor.plus(Duration.ofHours(2)),
        )
        assertEquals(4, occs.size)
    }

    @Test
    fun `current phase is resolved from time within the cycle`() {
        val def = fixedIntervalEvent()
        // 2 minutos dentro del ciclo: fase de preparación (300 s).
        val phase = scheduler.currentPhase(def, anchor.plus(Duration.ofMinutes(2)))
        assertEquals(1, phase!!.order)
        // 6 minutos: ya en Oleada 1 (sin duración = abierta).
        val phase2 = scheduler.currentPhase(def, anchor.plus(Duration.ofMinutes(6)))
        assertEquals(2, phase2!!.order)
    }

    @Test
    fun `occurrences carry the definition confidence`() {
        val def = fixedIntervalEvent().copy(defaultConfidence = Confidence.CONFIRMED)
        assertEquals(Confidence.CONFIRMED, scheduler.nextOccurrence(def, anchor)!!.confidence)
    }

    @Test
    fun `weekly schedule resolves in realm timezone`() {
        val def = fixedIntervalEvent().copy(
            id = "soiree",
            cadence = EventCadence.WeeklySchedule(
                listOf(DayTime(DayOfWeek.FRIDAY, LocalTime.of(18, 0))),
            ),
        )
        // Miércoles 2026-07-22 12:00 hora del reino.
        val from = java.time.ZonedDateTime.of(2026, 7, 22, 12, 0, 0, 0, zone).toInstant()
        val occ = scheduler.nextOccurrence(def, from)!!
        val startLocal = java.time.ZonedDateTime.ofInstant(occ.startsAt, zone)
        assertEquals(DayOfWeek.FRIDAY, startLocal.dayOfWeek)
        assertEquals(18, startLocal.hour)
        assertTrue(occ.startsAt.isAfter(from))
    }
}
