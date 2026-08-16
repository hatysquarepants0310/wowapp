package com.azeroth.companion.core.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * La caché de disco es lo que evita re-decodificar BLP cada vez que se abre
 * una zona. Si un archivo está corrupto tiene que devolver null (y borrarlo)
 * para que el loader vuelva al BLP; nunca un lienzo en blanco.
 */
class DecodedTileCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun cache() = DecodedTileCache(tmp.newFolder("maptiles"))

    private fun pixels(width: Int = 4, height: Int = 2, fill: Int = 0xFF112233.toInt()) =
        BlpDecoder.Pixels(width, height, IntArray(width * height) { fill })

    @Test
    fun `tras un decode se puede releer el mismo tile sin pasar por BLP`() {
        val cache = cache()
        val original = pixels()
        assertTrue(cache.write(fileId = 42, pixels = original))

        val loaded = cache.read(42)
        assertNotNull(loaded)
        loaded!!
        assertEquals(original.width, loaded.width)
        assertEquals(original.height, loaded.height)
        assertArrayEquals(original.argb, loaded.argb)
    }

    @Test
    fun `un miss no inventa pixels`() {
        assertNull(cache().read(99))
    }

    @Test
    fun `un archivo corrupto se descarta y se borra`() {
        val cache = cache()
        cache.write(7, pixels())
        val file = cache.fileFor(7)
        assertTrue(file.exists())
        file.writeBytes("not a decoded tile".toByteArray())

        assertNull(cache.read(7))
        assertFalse(file.exists())
    }

    @Test
    fun `un archivo truncado se descarta`() {
        val cache = cache()
        cache.write(8, pixels())
        val file = cache.fileFor(8)
        file.writeBytes(file.readBytes().copyOf(8))

        assertNull(cache.read(8))
        assertFalse(file.exists())
    }

    @Test
    fun `la clave incluye version - un formato viejo no se reutiliza`() {
        val cache = cache()
        cache.write(3, pixels())
        val file = cache.fileFor(3)
        assertTrue(file.name.endsWith(".azt1"))
        val bytes = file.readBytes()
        bytes[3] = '0'.code.toByte()
        file.writeBytes(bytes)

        assertNull(cache.read(3))
    }

    @Test
    fun `un miss se rellena con el decode de BLP y el segundo read no necesita el BLP`() {
        val cache = cache()
        val header = ByteArray(148 + 256 * 4)
        "BLP2".forEachIndexed { i, c -> header[i] = c.code.toByte() }
        header[8] = 3
        writeInt(header, 12, 1)
        writeInt(header, 16, 1)
        writeInt(header, 20, header.size)
        writeInt(header, 84, 4)
        val blp = header + byteArrayOf(1, 2, 3, 4)

        val decoded = BlpDecoder.decodePixels(blp)
        assertNotNull(decoded)
        assertTrue(cache.write(11, decoded!!))

        // El BLP ya no hace falta: la caché sola reproduce los píxeles.
        val loaded = cache.read(11)!!
        assertEquals(0x04030201, loaded.argb[0])
    }

    private fun writeInt(dst: ByteArray, at: Int, value: Int) {
        dst[at] = (value and 0xFF).toByte()
        dst[at + 1] = ((value shr 8) and 0xFF).toByte()
        dst[at + 2] = ((value shr 16) and 0xFF).toByte()
        dst[at + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
