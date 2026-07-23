package com.azeroth.companion.core.time

import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.EventCadence
import java.time.Duration
import java.time.Instant

/**
 * Auto-calibración del anclaje de eventos (§4.4). El usuario pulsa
 * "El evento acaba de empezar"; con ≥3 observaciones consistentes el anclaje
 * se recalcula y la confianza sube a CONFIRMED. Esto convierte al usuario en la
 * fuente de verdad y elimina la dependencia de un catálogo remoto mantenido.
 */
class AnchorCalibrator(
    private val minObservations: Int = 3,
    private val toleranceSeconds: Long = 120,
) {

    data class Result(
        val calibratedAnchor: Instant?,
        val confidence: Confidence,
        val consistentObservations: Int,
    )

    fun calibrate(cadence: EventCadence.FixedInterval, observations: List<Instant>): Result {
        if (observations.size < minObservations) {
            return Result(null, Confidence.PREDICTED, observations.size)
        }
        val intervalSec = Duration.ofMinutes(cadence.intervalMinutes.toLong()).seconds
        // Offset de cada observación dentro del ciclo, respecto al anclaje actual.
        val offsets = observations.map {
            Math.floorMod(Duration.between(cadence.anchorUtc, it).seconds, intervalSec)
        }
        // Mediana circular simple: ordenar y verificar dispersión respecto a la mediana.
        val sorted = offsets.sorted()
        val median = sorted[sorted.size / 2]
        val consistent = offsets.filter { circularDistance(it, median, intervalSec) <= toleranceSeconds }
        if (consistent.size < minObservations) {
            return Result(null, Confidence.PREDICTED, consistent.size)
        }
        val meanOffset = consistent
            .map { signedCircularDelta(it, median, intervalSec) }
            .average().toLong() + median
        return Result(
            calibratedAnchor = cadence.anchorUtc.plusSeconds(Math.floorMod(meanOffset, intervalSec)),
            confidence = Confidence.CONFIRMED,
            consistentObservations = consistent.size,
        )
    }

    private fun circularDistance(a: Long, b: Long, mod: Long): Long {
        val d = Math.floorMod(a - b, mod)
        return minOf(d, mod - d)
    }

    private fun signedCircularDelta(a: Long, b: Long, mod: Long): Long {
        val d = Math.floorMod(a - b, mod)
        return if (d > mod / 2) d - mod else d
    }
}
