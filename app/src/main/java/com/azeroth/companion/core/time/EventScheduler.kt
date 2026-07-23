package com.azeroth.companion.core.time

import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.EventCadence
import com.azeroth.companion.core.model.EventOccurrence
import com.azeroth.companion.core.model.EventPhase
import com.azeroth.companion.core.model.WorldEventDefinition
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

interface EventScheduler {
    fun nextOccurrence(def: WorldEventDefinition, from: Instant): EventOccurrence?
    fun occurrencesInRange(def: WorldEventDefinition, range: ClosedRange<Instant>): List<EventOccurrence>
    fun currentPhase(def: WorldEventDefinition, at: Instant): EventPhase?
    fun timeUntilNextStart(def: WorldEventDefinition, from: Instant): Duration?
}

/**
 * Todo cálculo se hace en la zona horaria del reino ([realmZone]), no la del dispositivo (§4.3).
 * La confianza de cada ocurrencia proviene de la definición: PREDICTED por defecto,
 * CONFIRMED cuando el anclaje fue calibrado con observaciones del usuario (§4.4).
 */
class DefaultEventScheduler(private val realmZone: ZoneId) : EventScheduler {

    override fun nextOccurrence(def: WorldEventDefinition, from: Instant): EventOccurrence? {
        val start = nextStart(def.cadence, from) ?: return null
        return EventOccurrence(
            definitionId = def.id,
            startsAt = start,
            endsAt = start.plus(estimatedDuration(def)),
            confidence = def.defaultConfidence,
        )
    }

    override fun occurrencesInRange(
        def: WorldEventDefinition,
        range: ClosedRange<Instant>,
    ): List<EventOccurrence> {
        val out = mutableListOf<EventOccurrence>()
        var cursor = range.start.minusSeconds(1)
        while (true) {
            val occ = nextOccurrence(def, cursor) ?: break
            if (occ.startsAt > range.endInclusive) break
            out += occ
            cursor = occ.startsAt
            if (out.size > 500) break // salvaguarda contra catálogos corruptos
        }
        return out
    }

    override fun currentPhase(def: WorldEventDefinition, at: Instant): EventPhase? {
        val cadence = def.cadence
        if (cadence !is EventCadence.FixedInterval) return null
        val interval = Duration.ofMinutes(cadence.intervalMinutes.toLong())
        val sinceAnchor = Duration.between(anchor(cadence), at)
        if (sinceAnchor.isNegative) return null
        val intoCycle = Duration.ofSeconds(sinceAnchor.seconds % interval.seconds)
        var elapsed = Duration.ZERO
        for (phase in def.phases.sortedBy { it.order }) {
            val dur = phase.durationSeconds?.let { Duration.ofSeconds(it.toLong()) } ?: return phase
            if (intoCycle < elapsed + dur) return phase
            elapsed += dur
        }
        return null
    }

    override fun timeUntilNextStart(def: WorldEventDefinition, from: Instant): Duration? =
        nextStart(def.cadence, from)?.let { Duration.between(from, it) }

    private fun anchor(c: EventCadence.FixedInterval): Instant =
        c.anchorUtc.plus(Duration.ofMinutes(c.offsetMinutes.toLong()))

    private fun nextStart(cadence: EventCadence, from: Instant): Instant? = when (cadence) {
        is EventCadence.FixedInterval -> {
            val interval = Duration.ofMinutes(cadence.intervalMinutes.toLong()).seconds
            val anchor = anchor(cadence)
            val delta = Duration.between(anchor, from).seconds
            val cycles = if (delta < 0) 0 else delta / interval + 1
            anchor.plusSeconds(cycles * interval)
        }

        is EventCadence.WeeklySchedule -> cadence.entries.mapNotNull { entry ->
            val zdt = ZonedDateTime.ofInstant(from, realmZone)
            var candidate = zdt.with(TemporalAdjusters.nextOrSame(entry.dayOfWeek)).with(entry.time)
            if (!candidate.toInstant().isAfter(from)) {
                candidate = candidate.with(TemporalAdjusters.next(entry.dayOfWeek))
            }
            candidate.toInstant()
        }.minOrNull()

        is EventCadence.RefreshWindows -> cadence.daysOfWeek.mapNotNull { day ->
            val zdt = ZonedDateTime.ofInstant(from, realmZone)
            var candidate = zdt.with(TemporalAdjusters.nextOrSame(day)).with(cadence.timeOfDay)
            if (!candidate.toInstant().isAfter(from)) {
                candidate = candidate.with(TemporalAdjusters.next(day))
            }
            candidate.toInstant()
        }.minOrNull()

        is EventCadence.Continuous -> null
    }

    private fun estimatedDuration(def: WorldEventDefinition): Duration {
        val phaseTotal = def.phases.sumOf { (it.durationSeconds ?: 0).toLong() }
        return when (val c = def.cadence) {
            is EventCadence.FixedInterval ->
                if (phaseTotal > 0) Duration.ofSeconds(phaseTotal)
                else Duration.ofMinutes(c.intervalMinutes.toLong() / 2)
            is EventCadence.Continuous -> Duration.ofHours(c.minDurationHours.toLong())
            else -> if (phaseTotal > 0) Duration.ofSeconds(phaseTotal) else Duration.ofHours(1)
        }
    }
}
