package com.azeroth.companion.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El placeholder eterno no era el texto: era esperar 12–150 BLP de wago
 * (o un compose que devolvía null) antes de tocar el JPEG de zamimg.
 * El primer paint tiene que ser UN archivo, con timeout corto.
 */
class ZoneMapFirstPaintTest {

    @Test
    fun `la primera fuente es un solo JPEG, el BLP va detras`() {
        val sources = ZoneMapFirstPaint.sources()
        assertEquals(listOf(ZoneMapFirstPaint.Source.JPEG, ZoneMapFirstPaint.Source.BLP), sources)
        assertTrue(ZoneMapFirstPaint.firstSourceIsSingleFile)
    }

    @Test
    fun `un JPEG listo pinta aunque el BLP no haya empezado`() {
        assertEquals(
            ZoneMapFirstPaint.Decision.ShowJpeg,
            ZoneMapFirstPaint.decide(elapsedMs = 1_200, jpegReady = true, blpReady = false),
        )
    }

    @Test
    fun `si el timeout se agota sin arte el resultado es error, no esperar`() {
        assertEquals(
            ZoneMapFirstPaint.Decision.Fail,
            ZoneMapFirstPaint.decide(
                elapsedMs = ZoneMapFirstPaint.TIMEOUT_MS,
                jpegReady = false,
                blpReady = false,
            ),
        )
    }

    @Test
    fun `dentro del timeout y sin arte todavia se espera`() {
        assertEquals(
            ZoneMapFirstPaint.Decision.KeepWaiting,
            ZoneMapFirstPaint.decide(elapsedMs = 100, jpegReady = false, blpReady = false),
        )
    }

    @Test
    fun `el BLP sustituye al JPEG cuando llega`() {
        assertEquals(
            ZoneMapFirstPaint.Decision.ShowBlp,
            ZoneMapFirstPaint.decide(elapsedMs = 4_000, jpegReady = true, blpReady = true),
        )
    }

    @Test
    fun `loadingMaps se apaga al terminar aunque haya fallado`() {
        assertEquals(false, ZoneMapFirstPaint.loadingMapsAfterAttempt(finished = true, threw = false))
        assertEquals(false, ZoneMapFirstPaint.loadingMapsAfterAttempt(finished = false, threw = true))
        assertEquals(true, ZoneMapFirstPaint.loadingMapsAfterAttempt(finished = false, threw = false))
    }
}
