package com.azeroth.companion.core.map

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Decodificador de BLP2, el formato de textura del cliente de World of Warcraft.
 *
 * Hace falta porque los mapas de zona se publican en BLP y Android no lo
 * entiende. Se decodifica en el dispositivo, en el momento de mirar el mapa: el
 * APK no lleva ni una sola textura de Blizzard dentro.
 *
 * Cabecera BLP2 (little-endian):
 * ```
 *   0  char[4]  "BLP2"
 *   4  uint32   versión de formato (1)
 *   8  uint8    codificación de color   1 = paleta, 2 = DXT, 3 = ARGB8888
 *   9  uint8    bits de alfa            0, 1, 4 u 8
 *  10  uint8    formato preferido       distingue DXT1 / DXT3 / DXT5
 *  11  uint8    tiene mipmaps
 *  12  uint32   ancho
 *  16  uint32   alto
 *  20  uint32   offset de cada mipmap  [16]
 *  84  uint32   tamaño de cada mipmap  [16]
 * 148  uint32   paleta BGRA            [256]   (siempre presente en BLP2)
 * ```
 *
 * Los mapas de zona que usa la app son DXT1 de 256x256 sin alfa, pero se
 * cubren también las demás variantes: una textura suelta con otro formato no
 * debe dejar el mapa en blanco.
 */
object BlpDecoder {

    private const val HEADER_SIZE = 148
    private const val PALETTE_SIZE = 256 * 4
    private const val DATA_START = HEADER_SIZE + PALETTE_SIZE

    private const val ENCODING_PALETTE = 1
    private const val ENCODING_DXT = 2
    private const val ENCODING_ARGB = 3

    /** Píxeles ya decodificados, sin depender de Android: es lo que se prueba. */
    data class Pixels(val width: Int, val height: Int, val argb: IntArray) {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = width * 31 + height
    }

    /** Devuelve null si el búfer no es un BLP2 que sepamos leer. */
    fun decode(bytes: ByteArray): Bitmap? {
        val p = decodePixels(bytes) ?: return null
        return Bitmap.createBitmap(p.argb, p.width, p.height, Bitmap.Config.ARGB_8888)
    }

    /** Igual que [decode] pero sin construir el bitmap de Android. */
    fun decodePixels(bytes: ByteArray): Pixels? {
        if (bytes.size < DATA_START) return null
        if (bytes[0] != 'B'.code.toByte() || bytes[1] != 'L'.code.toByte() ||
            bytes[2] != 'P'.code.toByte() || bytes[3] != '2'.code.toByte()
        ) {
            return null
        }

        val encoding = bytes[8].toInt() and 0xFF
        val alphaSize = bytes[9].toInt() and 0xFF
        val alphaType = bytes[10].toInt() and 0xFF
        val width = readInt(bytes, 12)
        val height = readInt(bytes, 16)
        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE) return null

        // Solo interesa el mipmap 0: es el de resolución completa.
        val offset = readInt(bytes, 20)
        val size = readInt(bytes, 84)
        if (offset <= 0 || size <= 0 || offset + size > bytes.size) return null

        val pixels = when (encoding) {
            ENCODING_DXT -> decodeDxt(bytes, offset, width, height, alphaSize, alphaType)
            ENCODING_PALETTE -> decodePalette(bytes, offset, width, height, alphaSize)
            ENCODING_ARGB -> decodeArgb(bytes, offset, width, height)
            else -> null
        } ?: return null

        return Pixels(width, height, pixels)
    }

    // ---- DXT ---------------------------------------------------------------

    private fun decodeDxt(
        src: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
        alphaSize: Int,
        alphaType: Int,
    ): IntArray? {
        // alphaSize 0 o 1 → DXT1. Con 8 bits, `alphaType` distingue el explícito
        // de 4 bits (DXT3) del interpolado (DXT5).
        val format = when {
            alphaSize <= 1 -> Dxt.DXT1
            alphaType == 7 -> Dxt.DXT5
            else -> Dxt.DXT3
        }
        val blocksX = (width + 3) / 4
        val blocksY = (height + 3) / 4
        val blockBytes = if (format == Dxt.DXT1) 8 else 16
        if (offset + blocksX * blocksY * blockBytes > src.size) return null

        val out = IntArray(width * height)
        val colors = IntArray(4)
        val alphas = IntArray(8)
        var pos = offset

        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                var alphaBits = 0L
                var explicitAlpha = 0L
                when (format) {
                    Dxt.DXT5 -> {
                        alphas[0] = src[pos].toInt() and 0xFF
                        alphas[1] = src[pos + 1].toInt() and 0xFF
                        // Los seis intermedios se interpolan de distinta forma
                        // según cuál de los dos extremos sea mayor.
                        if (alphas[0] > alphas[1]) {
                            for (i in 1..6) {
                                alphas[i + 1] = ((7 - i) * alphas[0] + i * alphas[1]) / 7
                            }
                        } else {
                            for (i in 1..4) {
                                alphas[i + 1] = ((5 - i) * alphas[0] + i * alphas[1]) / 5
                            }
                            alphas[6] = 0
                            alphas[7] = 255
                        }
                        alphaBits = 0
                        for (i in 0 until 6) {
                            alphaBits = alphaBits or
                                ((src[pos + 2 + i].toLong() and 0xFF) shl (8 * i))
                        }
                        pos += 8
                    }
                    Dxt.DXT3 -> {
                        for (i in 0 until 8) {
                            explicitAlpha = explicitAlpha or
                                ((src[pos + i].toLong() and 0xFF) shl (8 * i))
                        }
                        pos += 8
                    }
                    Dxt.DXT1 -> Unit
                }

                val c0 = (src[pos].toInt() and 0xFF) or ((src[pos + 1].toInt() and 0xFF) shl 8)
                val c1 = (src[pos + 2].toInt() and 0xFF) or ((src[pos + 3].toInt() and 0xFF) shl 8)
                colors[0] = rgb565(c0)
                colors[1] = rgb565(c1)
                // En DXT1, c0 <= c1 significa que el cuarto color es transparente
                // y el tercero es la media simple. En DXT3/DXT5 siempre se usa la
                // variante de cuatro colores opacos.
                if (c0 > c1 || format != Dxt.DXT1) {
                    colors[2] = lerp(colors[0], colors[1], 2, 1)
                    colors[3] = lerp(colors[0], colors[1], 1, 2)
                } else {
                    colors[2] = lerp(colors[0], colors[1], 1, 1)
                    colors[3] = 0
                }
                var indices = 0L
                for (i in 0 until 4) {
                    indices = indices or ((src[pos + 4 + i].toLong() and 0xFF) shl (8 * i))
                }
                pos += 8

                for (py in 0 until 4) {
                    val y = by * 4 + py
                    if (y >= height) break
                    for (px in 0 until 4) {
                        val x = bx * 4 + px
                        if (x >= width) continue
                        val bit = (py * 4 + px)
                        val color = colors[((indices shr (bit * 2)) and 3L).toInt()]
                        val alpha = when (format) {
                            Dxt.DXT1 -> if (color == 0) 0 else 255
                            Dxt.DXT3 -> {
                                val nibble = ((explicitAlpha shr (bit * 4)) and 0xFL).toInt()
                                nibble * 17
                            }
                            Dxt.DXT5 -> alphas[((alphaBits shr (bit * 3)) and 7L).toInt()]
                        }
                        out[y * width + x] = (alpha shl 24) or (color and 0x00FFFFFF)
                    }
                }
            }
        }
        return out
    }

    private enum class Dxt { DXT1, DXT3, DXT5 }

    // ---- Paleta y ARGB -----------------------------------------------------

    private fun decodePalette(
        src: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
        alphaSize: Int,
    ): IntArray? {
        val count = width * height
        if (offset + count > src.size) return null
        val palette = IntArray(256) { i ->
            val p = HEADER_SIZE + i * 4
            // La paleta va en BGRA; su byte de alfa no se usa.
            Color.rgb(
                src[p + 2].toInt() and 0xFF,
                src[p + 1].toInt() and 0xFF,
                src[p].toInt() and 0xFF,
            )
        }
        val out = IntArray(count)
        for (i in 0 until count) {
            out[i] = palette[src[offset + i].toInt() and 0xFF]
        }
        if (alphaSize == 8) {
            val alphaStart = offset + count
            if (alphaStart + count <= src.size) {
                for (i in 0 until count) {
                    val a = src[alphaStart + i].toInt() and 0xFF
                    out[i] = (a shl 24) or (out[i] and 0x00FFFFFF)
                }
            }
        }
        return out
    }

    private fun decodeArgb(src: ByteArray, offset: Int, width: Int, height: Int): IntArray? {
        val count = width * height
        if (offset + count * 4 > src.size) return null
        val out = IntArray(count)
        for (i in 0 until count) {
            val p = offset + i * 4
            val b = src[p].toInt() and 0xFF
            val g = src[p + 1].toInt() and 0xFF
            val r = src[p + 2].toInt() and 0xFF
            val a = src[p + 3].toInt() and 0xFF
            out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    // ---- Utilidades --------------------------------------------------------

    private fun readInt(src: ByteArray, at: Int): Int =
        (src[at].toInt() and 0xFF) or
            ((src[at + 1].toInt() and 0xFF) shl 8) or
            ((src[at + 2].toInt() and 0xFF) shl 16) or
            ((src[at + 3].toInt() and 0xFF) shl 24)

    /** RGB565 → RGB888, replicando los bits altos para no perder el blanco. */
    private fun rgb565(value: Int): Int {
        val r = (value shr 11) and 0x1F
        val g = (value shr 5) and 0x3F
        val b = value and 0x1F
        return (((r shl 3) or (r shr 2)) shl 16) or
            (((g shl 2) or (g shr 4)) shl 8) or
            ((b shl 3) or (b shr 2))
    }

    private fun lerp(a: Int, b: Int, wa: Int, wb: Int): Int {
        val total = wa + wb
        val r = (((a shr 16) and 0xFF) * wa + ((b shr 16) and 0xFF) * wb) / total
        val g = (((a shr 8) and 0xFF) * wa + ((b shr 8) and 0xFF) * wb) / total
        val bl = ((a and 0xFF) * wa + (b and 0xFF) * wb) / total
        return (r shl 16) or (g shl 8) or bl
    }

    /** Un tile de mapa son 256x256; nada legítimo se acerca a este límite. */
    private const val MAX_SIDE = 8192
}
