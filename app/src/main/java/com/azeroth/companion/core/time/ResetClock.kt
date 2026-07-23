package com.azeroth.companion.core.time

import com.azeroth.companion.core.model.Region
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Reglas de reset por región. Viven en el catálogo JSON (§4.2), nunca hardcodeadas:
 * Blizzard las ha movido entre parches y difieren por región.
 */
@Serializable
data class ResetRules(
    val region: Region,
    /** Zona horaria de referencia de los reinos de la región. */
    val realmZoneId: String,
    val weeklyResetDay: DayOfWeek,
    /** Hora del reset semanal, en la zona indicada por [resetZoneId]. */
    val weeklyResetTime: String,
    /** Hora del reset diario, en la zona indicada por [resetZoneId]. */
    val dailyResetTime: String,
    /** Zona en la que están expresadas las horas de reset (UTC evita sorpresas de DST). */
    val resetZoneId: String = "Z",
)

class ResetClock(private val rules: ResetRules) {

    private val zone: ZoneId = ZoneId.of(rules.resetZoneId)
    val realmZone: ZoneId = ZoneId.of(rules.realmZoneId)

    fun nextWeeklyReset(from: Instant): Instant {
        val time = LocalTime.parse(rules.weeklyResetTime)
        val zdt = ZonedDateTime.ofInstant(from, zone)
        var candidate = zdt.with(TemporalAdjusters.nextOrSame(rules.weeklyResetDay)).with(time)
        if (!candidate.toInstant().isAfter(from)) {
            candidate = candidate.with(TemporalAdjusters.next(rules.weeklyResetDay))
        }
        return candidate.toInstant()
    }

    fun lastWeeklyReset(from: Instant): Instant {
        val time = LocalTime.parse(rules.weeklyResetTime)
        val zdt = ZonedDateTime.ofInstant(from, zone)
        var candidate = zdt.with(TemporalAdjusters.previousOrSame(rules.weeklyResetDay)).with(time)
        if (candidate.toInstant().isAfter(from)) {
            candidate = candidate.with(TemporalAdjusters.previous(rules.weeklyResetDay))
        }
        return candidate.toInstant()
    }

    fun nextDailyReset(from: Instant): Instant {
        val time = LocalTime.parse(rules.dailyResetTime)
        val zdt = ZonedDateTime.ofInstant(from, zone)
        var candidate = zdt.with(time)
        if (!candidate.toInstant().isAfter(from)) candidate = candidate.plusDays(1)
        return candidate.toInstant()
    }

    /** true si [instant] pertenece a la semana de juego en curso respecto a [now]. */
    fun isInCurrentGameWeek(instant: Instant, now: Instant): Boolean =
        !instant.isBefore(lastWeeklyReset(now)) && instant.isBefore(nextWeeklyReset(now))
}
