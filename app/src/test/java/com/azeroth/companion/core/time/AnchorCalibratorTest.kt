package com.azeroth.companion.core.time

import com.azeroth.companion.core.model.Confidence
import com.azeroth.companion.core.model.EventCadence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AnchorCalibratorTest {

    private val anchor = Instant.parse("2026-07-21T15:00:00Z")
    private val cadence = EventCadence.FixedInterval(30, anchor)
    private val calibrator = AnchorCalibrator()

    @Test
    fun `fewer than 3 observations keeps PREDICTED`() {
        val result = calibrator.calibrate(cadence, listOf(anchor.plusSeconds(60)))
        assertNull(result.calibratedAnchor)
        assertEquals(Confidence.PREDICTED, result.confidence)
    }

    @Test
    fun `3 consistent observations confirm and shift the anchor`() {
        // El evento en realidad empieza 5 min después de lo que dice el catálogo.
        val offset = Duration.ofMinutes(5)
        val observations = (1..3).map {
            anchor.plus(Duration.ofMinutes(30L * it)).plus(offset)
        }
        val result = calibrator.calibrate(cadence, observations)
        assertNotNull(result.calibratedAnchor)
        assertEquals(Confidence.CONFIRMED, result.confidence)
        // El nuevo anclaje debe caer en la retícula real observada.
        val delta = Duration.between(anchor, result.calibratedAnchor).seconds % (30 * 60)
        assertEquals(offset.seconds, delta)
    }

    @Test
    fun `inconsistent observations do not calibrate`() {
        val observations = listOf(
            anchor.plusSeconds(0),
            anchor.plusSeconds(600),
            anchor.plusSeconds(1200),
        )
        val result = calibrator.calibrate(cadence, observations)
        assertNull(result.calibratedAnchor)
        assertEquals(Confidence.PREDICTED, result.confidence)
    }
}
