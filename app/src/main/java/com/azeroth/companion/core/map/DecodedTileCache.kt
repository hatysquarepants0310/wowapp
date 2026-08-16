package com.azeroth.companion.core.map

import java.io.File

/**
 * Caché en disco de tiles YA decodificados.
 *
 * El cuello de botella al reabrir una zona no era la red: el `.blp` ya estaba
 * en `maptiles/`. Lo lento era volver a desempaquetar DXT en cada apertura.
 * Aquí se guarda el ARGB resultante, keyed por el fileId del CASC (la
 * identidad estable del tile) y por la versión del formato (`azt1`), para no
 * reutilizar basura de un encoder viejo.
 *
 * Un miss o un archivo corrupto devuelve null y, si había archivo, lo borra.
 * El caller vuelve a decodificar el BLP. Nunca se inventan píxeles en blanco.
 */
class DecodedTileCache(private val dir: File) {

    init {
        dir.mkdirs()
    }

    fun fileFor(fileId: Int): File = File(dir, "$fileId.$SUFFIX")

    fun read(fileId: Int): BlpDecoder.Pixels? {
        val file = fileFor(fileId)
        if (!file.exists()) return null
        val bytes = runCatching { file.readBytes() }.getOrNull()
        val pixels = bytes?.let { decode(it) }
        if (pixels == null) {
            runCatching { file.delete() }
        }
        return pixels
    }

    fun write(fileId: Int, pixels: BlpDecoder.Pixels): Boolean {
        if (pixels.width <= 0 || pixels.height <= 0) return false
        if (pixels.argb.size != pixels.width * pixels.height) return false
        dir.mkdirs()
        val target = fileFor(fileId)
        val tmp = File(dir, "${fileId}.$SUFFIX.tmp")
        return runCatching {
            tmp.writeBytes(encode(pixels))
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            true
        }.getOrElse {
            runCatching { tmp.delete() }
            false
        }
    }

    private companion object {
        const val SUFFIX = "azt1"
        const val MAGIC = "AZT1"
        const val HEADER_SIZE = 12
        const val MAX_SIDE = 8192

        fun encode(pixels: BlpDecoder.Pixels): ByteArray {
            val out = ByteArray(HEADER_SIZE + pixels.argb.size * 4)
            MAGIC.forEachIndexed { i, c -> out[i] = c.code.toByte() }
            writeInt(out, 4, pixels.width)
            writeInt(out, 8, pixels.height)
            var at = HEADER_SIZE
            for (pixel in pixels.argb) {
                writeInt(out, at, pixel)
                at += 4
            }
            return out
        }

        fun decode(bytes: ByteArray): BlpDecoder.Pixels? {
            if (bytes.size < HEADER_SIZE) return null
            if (bytes[0] != 'A'.code.toByte() || bytes[1] != 'Z'.code.toByte() ||
                bytes[2] != 'T'.code.toByte() || bytes[3] != '1'.code.toByte()
            ) {
                return null
            }
            val width = readInt(bytes, 4)
            val height = readInt(bytes, 8)
            if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE) return null
            val count = width * height
            if (bytes.size != HEADER_SIZE + count * 4) return null
            val argb = IntArray(count)
            var at = HEADER_SIZE
            for (i in 0 until count) {
                argb[i] = readInt(bytes, at)
                at += 4
            }
            return BlpDecoder.Pixels(width, height, argb)
        }

        fun writeInt(dst: ByteArray, at: Int, value: Int) {
            dst[at] = (value and 0xFF).toByte()
            dst[at + 1] = ((value shr 8) and 0xFF).toByte()
            dst[at + 2] = ((value shr 16) and 0xFF).toByte()
            dst[at + 3] = ((value shr 24) and 0xFF).toByte()
        }

        fun readInt(src: ByteArray, at: Int): Int =
            (src[at].toInt() and 0xFF) or
                ((src[at + 1].toInt() and 0xFF) shl 8) or
                ((src[at + 2].toInt() and 0xFF) shl 16) or
                ((src[at + 3].toInt() and 0xFF) shl 24)
    }
}
