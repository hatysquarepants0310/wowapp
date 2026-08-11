package com.azeroth.companion.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El decodificador de BLP es lo que hace que la app pueda pintar el mapa real
 * del juego, así que su aritmética tiene que estar fijada: un error de un bit en
 * el desempaquetado de DXT1 no da un fallo, da un mapa con los colores mal y
 * nadie sabría por qué.
 *
 * Los BLP de estas pruebas se construyen a mano con la misma cabecera que
 * publica el cliente.
 */
class BlpDecoderTest {

    private fun blp(
        encoding: Int,
        alphaSize: Int,
        alphaType: Int,
        width: Int,
        height: Int,
        payload: ByteArray,
    ): ByteArray {
        val header = ByteArray(148 + 256 * 4)
        "BLP2".forEachIndexed { i, c -> header[i] = c.code.toByte() }
        writeInt(header, 4, 1)
        header[8] = encoding.toByte()
        header[9] = alphaSize.toByte()
        header[10] = alphaType.toByte()
        header[11] = 0
        writeInt(header, 12, width)
        writeInt(header, 16, height)
        writeInt(header, 20, header.size)      // offset del mipmap 0
        writeInt(header, 84, payload.size)     // tamaño del mipmap 0
        return header + payload
    }

    private fun writeInt(dst: ByteArray, at: Int, value: Int) {
        dst[at] = (value and 0xFF).toByte()
        dst[at + 1] = ((value shr 8) and 0xFF).toByte()
        dst[at + 2] = ((value shr 16) and 0xFF).toByte()
        dst[at + 3] = ((value shr 24) and 0xFF).toByte()
    }

    /** Un bloque DXT1 de 4x4: rojo y azul puros, con los cuatro índices. */
    private fun dxt1Block(c0: Int, c1: Int, indices: Long): ByteArray = byteArrayOf(
        (c0 and 0xFF).toByte(), ((c0 shr 8) and 0xFF).toByte(),
        (c1 and 0xFF).toByte(), ((c1 shr 8) and 0xFF).toByte(),
        (indices and 0xFF).toByte(),
        ((indices shr 8) and 0xFF).toByte(),
        ((indices shr 16) and 0xFF).toByte(),
        ((indices shr 24) and 0xFF).toByte(),
    )

    @Test
    fun `descarta lo que no es BLP2`() {
        assertNull(BlpDecoder.decodePixels(ByteArray(2000)))
        assertNull(BlpDecoder.decodePixels("PNG".toByteArray()))
    }

    @Test
    fun `DXT1 saca los dos colores extremos tal cual`() {
        val red = 0xF800   // RGB565 rojo puro
        val blue = 0x001F  // RGB565 azul puro
        // índice 0 en el primer píxel, índice 1 en el segundo
        val block = dxt1Block(red, blue, 0b01L shl 2)
        val pixels = BlpDecoder.decodePixels(blp(2, 0, 0, 4, 4, block))
        assertNotNull(pixels)
        pixels!!
        assertEquals(4, pixels.width)
        assertEquals(0xFFFF0000.toInt(), pixels.argb[0])
        assertEquals(0xFF0000FF.toInt(), pixels.argb[1])
    }

    /**
     * Con c0 > c1 los dos colores intermedios se interpolan a 2/3 y 1/3. Es la
     * variante opaca y la que usan los mapas de zona.
     */
    @Test
    fun `DXT1 interpola los colores intermedios`() {
        val white = 0xFFFF
        val black = 0x0000
        val block = dxt1Block(white, black, (0b10L shl 4))
        val pixels = BlpDecoder.decodePixels(blp(2, 0, 0, 4, 4, block))!!
        val mid = pixels.argb[2] and 0xFF
        // 2/3 de 255 ≈ 170: gris claro, no blanco ni negro.
        assertEquals(170, mid)
        assertEquals(255, pixels.argb[2] ushr 24)
    }

    /**
     * Con c0 <= c1 el índice 3 es transparente. Si esto se lee mal, los mapas
     * salen con manchas negras donde debería verse el fondo.
     */
    @Test
    fun `DXT1 respeta el índice transparente`() {
        val block = dxt1Block(0x0000, 0xFFFF, 0b11L)
        val pixels = BlpDecoder.decodePixels(blp(2, 0, 0, 4, 4, block))!!
        assertEquals(0, pixels.argb[0] ushr 24)
    }

    @Test
    fun `ARGB8888 se lee en el orden BGRA del archivo`() {
        // Un píxel: B=1, G=2, R=3, A=4
        val payload = byteArrayOf(1, 2, 3, 4)
        val pixels = BlpDecoder.decodePixels(blp(3, 8, 0, 1, 1, payload))!!
        assertEquals(0x04030201, pixels.argb[0])
    }

    @Test
    fun `una cabecera que promete más datos de los que hay no revienta`() {
        val truncated = blp(2, 0, 0, 256, 256, ByteArray(16))
        assertNull(BlpDecoder.decodePixels(truncated))
    }
}
