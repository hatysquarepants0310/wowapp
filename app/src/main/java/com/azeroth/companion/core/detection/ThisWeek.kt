package com.azeroth.companion.core.detection

import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.WeekTrust

/**
 * Qué cuenta como "esta semana" cuando la API no fecha el hecho.
 *
 * Midnight ya no saca de `/quests/completed` el ID de una semanal al reset:
 * presencia absoluta es lifetime, no la semana en curso. M+ y bandas sí
 * traen periodo / `last_kill_timestamp`; si el perfil no se ha refrescado
 * desde el reset, un 0 no es un dato, es un perfil desfasado.
 */
object ThisWeek {

    fun questIdsDone(
        candidates: Collection<Int>,
        completedNow: Set<Int>,
        completedBeforeReset: Set<Int>?,
    ): Set<Int> {
        if (completedBeforeReset == null) return emptySet()
        return candidates.filter { it in completedNow && it !in completedBeforeReset }.toSet()
    }

    fun questConfidence(
        completedBeforeReset: Set<Int>?,
        doneThisWeek: Set<Int>,
    ): Confidence = when {
        completedBeforeReset == null -> Confidence.PREDICTED
        doneThisWeek.isNotEmpty() -> Confidence.CONFIRMED
        else -> Confidence.ESTIMATED
    }

    fun mythicKeys(
        livePeriodId: Int?,
        profilePeriodId: Int?,
        lastLoginMillis: Long?,
        lastResetMillis: Long,
        profileRunCount: Int,
        runTimestamps: List<Long> = emptyList(),
    ): MythicWeekSlice {
        val loginStale = lastLoginMillis != null && lastLoginMillis < lastResetMillis
        val bothPeriods = livePeriodId != null && profilePeriodId != null
        val periodMismatch = bothPeriods && livePeriodId != profilePeriodId
        if (loginStale || periodMismatch) {
            return MythicWeekSlice(0, stale = true, WeekTrust.STALE)
        }
        if (bothPeriods && livePeriodId == profilePeriodId) {
            return MythicWeekSlice(profileRunCount, stale = false, WeekTrust.CONFIRMED)
        }
        val dated = runTimestamps.count { it >= lastResetMillis }
        return MythicWeekSlice(dated, stale = false, WeekTrust.CONFIRMED)
    }

    fun raidKillsThisWeek(lastKillTimestamps: List<Long>, lastResetMillis: Long): Int =
        lastKillTimestamps.count { it >= lastResetMillis }

    fun countLabel(count: Int, stale: Boolean): String = if (stale) "—" else count.toString()

    fun trust(profileStale: Boolean, hasBaseline: Boolean, snapshotBeforeReset: Boolean): WeekTrust =
        when {
            profileStale || snapshotBeforeReset -> WeekTrust.STALE
            hasBaseline -> WeekTrust.CONFIRMED
            else -> WeekTrust.ESTIMATED
        }
}

data class MythicWeekSlice(
    val count: Int,
    val stale: Boolean,
    val trust: WeekTrust,
)
