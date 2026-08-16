package com.azeroth.companion.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo lento no era pintar pines: era esperar a que TODAS las casillas
 * (descarga + DXT) terminaran antes de enseñar la primera. El orden
 * tiene que ser caché ya decodificada primero; el placeholder se queda
 * hasta que hay un tile listo; un fallo de tile es error, no blanco.
 */
class ProgressiveTilesTest {

    @Test
    fun `las casillas en disco van antes que las que hay que bajar o decodificar`() {
        val fileIds = listOf(10, 20, 30, 40)
        val cached = setOf(30, 10)
        val order = ProgressiveTiles.schedule(fileIds) { it in cached }

        assertEquals(listOf(10, 30, 20, 40), order.map { it.value })
        assertEquals(listOf(0, 2, 1, 3), order.map { it.index })
    }

    @Test
    fun `sin ningun tile listo se mantiene el placeholder`() {
        assertEquals(
            ProgressiveTiles.Publish.HOLD,
            ProgressiveTiles.publish(drawn = 0, missing = 0, remaining = 4),
        )
    }

    @Test
    fun `el primer tile listo publica el bitmap parcial`() {
        assertEquals(
            ProgressiveTiles.Publish.PARTIAL,
            ProgressiveTiles.publish(drawn = 1, missing = 0, remaining = 3),
        )
    }

    @Test
    fun `al terminar con tiles suficientes el mapa queda listo`() {
        assertEquals(
            ProgressiveTiles.Publish.READY,
            ProgressiveTiles.publish(drawn = 11, missing = 1, remaining = 0),
        )
    }

    @Test
    fun `si fallan demasiados tiles el resultado es error explicito`() {
        assertEquals(
            ProgressiveTiles.Publish.FAILED,
            ProgressiveTiles.publish(drawn = 2, missing = 2, remaining = 0),
        )
    }

    @Test
    fun `un mapa vacio o todo fallido no deja el lienzo en blanco`() {
        assertEquals(
            ProgressiveTiles.Publish.FAILED,
            ProgressiveTiles.publish(drawn = 0, missing = 1, remaining = 0),
        )
        assertTrue(ProgressiveTiles.allowsPlaceholderUntilFirstTile)
    }
}
