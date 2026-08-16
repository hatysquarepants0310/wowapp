package com.azeroth.companion.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Primera pintura del mapa: nunca un lienzo vacío.
 *
 * Antes, entre el snapshot y el bitmap, la superficie salía sin arte y
 * sin mensaje. Si el tile fallaba, lo mismo: null silencioso. Estas
 * reglas fijan los dos estados que el usuario tiene que ver.
 */
class ZoneMapArtStateTest {

    @Test
    fun `sin bitmap y sin fallo es placeholder de carga`() {
        val state = ZoneMapArtState.resolve(hasBitmap = false, failedMessage = null)
        assertEquals(ZoneMapArtState.Loading, state)
    }

    @Test
    fun `bitmap listo gana aunque hubiera un fallo viejo`() {
        val state = ZoneMapArtState.resolve(hasBitmap = true, failedMessage = "roto")
        assertEquals(ZoneMapArtState.Ready, state)
    }

    @Test
    fun `tile fallido sin bitmap es error explicito`() {
        val state = ZoneMapArtState.resolve(hasBitmap = false, failedMessage = "No se pudo cargar")
        assertTrue(state is ZoneMapArtState.Failed)
        assertEquals("No se pudo cargar", (state as ZoneMapArtState.Failed).message)
    }

    @Test
    fun `arte desactivado no es error ni carga`() {
        val state = ZoneMapArtState.resolve(
            hasBitmap = false,
            failedMessage = null,
            fallback = true,
        )
        assertEquals(ZoneMapArtState.Fallback, state)
    }
}
