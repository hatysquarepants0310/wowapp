package com.azeroth.companion.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.collection.LruCache
import com.azeroth.companion.core.datastore.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Cuadrícula de tiles de una zona y su tamaño real. */
@Serializable
data class MapTileGrid(
    val r: Int = 0,
    val c: Int = 0,
    val w: Int = 0,
    val h: Int = 0,
    val t: List<Int> = emptyList(),
)

/**
 * Compone el mapa REAL de una zona del juego.
 *
 * El APK no lleva arte de Blizzard dentro: solo el índice de qué archivos
 * componen cada zona (`map_tiles.json`, números). Las texturas se descargan en
 * el dispositivo la primera vez que se mira una zona, se decodifican de BLP a
 * bitmap y quedan en la caché de la app. A partir de ahí el mapa abre sin red.
 *
 * Un mapa de zona son 12 casillas de 256x256 (unos 45 kB cada una) que se
 * descargan en paralelo; el conjunto se recorta al tamaño real, porque la
 * cuadrícula redondea hacia arriba y ese borde sobrante desplazaría los puntos.
 */
@Singleton
class ZoneMapLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) {

    private val gridMutex = Mutex()
    private var grids: Map<Int, MapTileGrid>? = null

    // La caché se mide en BYTES, no en número de mapas: contar piezas no dice
    // nada cuando una zona puede pesar diez veces más que otra.
    private val memory = object : LruCache<Int, Bitmap>(MEMORY_BUDGET_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, "maptiles").apply { mkdirs() }
    }

    /** ¿Hay mapa del juego para esta zona? */
    suspend fun hasMap(uiMapId: Int): Boolean = grids().containsKey(uiMapId)

    /**
     * Baja a disco las texturas de varias zonas de una vez, sin componer nada.
     *
     * Antes había que entrar zona por zona y pedir el mapa a mano, que es
     * justo lo que nadie quiere hacer. Ahora, en cuanto se sabe qué zonas le
     * interesan al jugador, se traen todas de fondo: cuando llegue a mirarlas
     * ya están.
     *
     * Se descarga en paralelo pero con un tope, para no abrir cuarenta
     * conexiones a la vez ni maltratar la fuente.
     */
    suspend fun prefetch(uiMapIds: Collection<Int>) {
        if (!settingsRepository.settings.first().downloadMapArt) return
        val grids = grids()
        val pending = uiMapIds.distinct()
            .mapNotNull { grids[it] }
            .flatMap { it.t }
            .distinct()
            .filterNot { File(cacheDir, "$it.blp").let { f -> f.exists() && f.length() > 0 } }
        if (pending.isEmpty()) return
        withContext(Dispatchers.IO) {
            val gate = Semaphore(MAX_PARALLEL)
            coroutineScope {
                pending.forEach { fileId ->
                    launch {
                        gate.withPermit {
                            download(fileId)?.let { data ->
                                runCatching { File(cacheDir, "$fileId.blp").writeBytes(data) }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Devuelve el mapa de la zona, o null si no hay o si falla la descarga.
     * Nunca lanza: un mapa que no carga degrada a la vista sin fondo.
     */
    suspend fun load(uiMapId: Int): Bitmap? {
        memory.get(uiMapId)?.let { return it }
        if (!settingsRepository.settings.first().downloadMapArt) return null
        val grid = grids()[uiMapId] ?: return null
        return runCatching { compose(grid) }.getOrNull()?.also {
            memory.put(uiMapId, it)
        }
    }

    /**
     * Compone las casillas ya a TAMAÑO DE PANTALLA.
     *
     * Bug que esto arregla: solo cargaba el primer mapa. Los mapas de alta
     * resolución son cuadrículas de 15x10 casillas, o sea 3840x2560 píxeles;
     * en ARGB_8888 eso son 39 MB por mapa, más otros 39 de la copia recortada.
     * El primero entraba, el segundo agotaba la memoria, y como `compose` va
     * envuelta en `runCatching` —que atrapa también OutOfMemoryError— el fallo
     * se tragaba en silencio y el resto de zonas salían vacías.
     *
     * Ahora se escala al vuelo mientras se dibuja: nunca existe el bitmap
     * gigante. Y en RGB_565, que basta porque un mapa de zona no tiene
     * transparencia y ocupa la mitad. Un mapa pasa de 39 MB a menos de 3.
     */
    private suspend fun compose(grid: MapTileGrid): Bitmap? = coroutineScope {
        val fullWidth = grid.c * TILE
        val fullHeight = grid.r * TILE
        // Se trabaja sobre el tamaño REAL del mapa, no sobre la cuadrícula: el
        // sobrante de redondeo no debe ocupar ni memoria ni desplazar los puntos.
        val realWidth = grid.w.coerceIn(1, fullWidth)
        val realHeight = grid.h.coerceIn(1, fullHeight)
        val scale = minOf(
            1f,
            MAX_SIDE.toFloat() / maxOf(realWidth, realHeight).toFloat(),
        )
        val outWidth = (realWidth * scale).toInt().coerceAtLeast(1)
        val outHeight = (realHeight * scale).toInt().coerceAtLeast(1)

        val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        var missing = 0

        // POR TANDAS, no todas de golpe.
        //
        // Aquí estaba el bug de "solo carga el primer mapa", que ya creí haber
        // arreglado una vez y no era. Entonces se arregló el bitmap de SALIDA
        // —escalarlo y pasarlo a RGB_565— pero esta parte seguía haciendo un
        // `async` por casilla y esperándolas todas juntas en una lista. Una
        // cuadrícula de 15x10 son 150 casillas de 256x256 descomprimidas y
        // vivas a la vez: unos 39 MB, exactamente el tamaño que se había
        // quitado del otro lado. El primer mapa entraba raspando y el segundo
        // se quedaba sin memoria.
        //
        // Y como `compose` va envuelta en `runCatching`, que atrapa también
        // OutOfMemoryError, el fallo se tragaba en silencio y la zona salía
        // vacía sin un solo mensaje. Por eso costó tanto verlo.
        //
        // Ahora se decodifica una tanda, se dibuja, se recicla y se pasa a la
        // siguiente: en memoria nunca hay más de MAX_PARALLEL casillas, unos
        // 2 MB en vez de 39, independientemente del tamaño del mapa.
        grid.t.withIndex().chunked(MAX_PARALLEL).forEach { chunk ->
            val decoded = chunk.map { (index, fileId) ->
                async(Dispatchers.IO) { index to tile(fileId) }
            }.map { it.await() }

            decoded.forEach { (index, bitmap) ->
                if (bitmap == null) {
                    missing++
                    return@forEach
                }
                val row = index / grid.c
                val col = index % grid.c
                val dst = RectF(
                    col * TILE * scale,
                    row * TILE * scale,
                    (col + 1) * TILE * scale,
                    (row + 1) * TILE * scale,
                )
                canvas.drawBitmap(bitmap, null, dst, paint)
                bitmap.recycle()
            }
        }

        // Si falta más de una casilla el mapa saldría con agujeros: mejor nada.
        if (missing > 1) {
            out.recycle()
            return@coroutineScope null
        }
        out
    }

    private fun tile(fileId: Int): Bitmap? {
        val cached = File(cacheDir, "$fileId.blp")
        val bytes = if (cached.exists() && cached.length() > 0) {
            runCatching { cached.readBytes() }.getOrNull()
        } else {
            download(fileId)?.also { data ->
                runCatching { cached.writeBytes(data) }
            }
        } ?: return null
        return BlpDecoder.decode(bytes)
    }

    private fun download(fileId: Int): ByteArray? {
        val request = Request.Builder()
            .url("$TILE_SOURCE/$fileId?download")
            // Sin User-Agent de navegador la fuente responde con una página de
            // bloqueo en vez del archivo.
            .header("User-Agent", USER_AGENT)
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()
            }
        }.getOrNull()
    }

    private suspend fun grids(): Map<Int, MapTileGrid> {
        grids?.let { return it }
        return gridMutex.withLock {
            grids?.let { return it }
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("catalog/map_tiles.json").bufferedReader().use { reader ->
                        json.decodeFromString(
                            MapSerializer(String.serializer(), MapTileGrid.serializer()),
                            reader.readText(),
                        ).mapKeys { it.key.toInt() }
                    }
                }.getOrDefault(emptyMap()).also { grids = it }
            }
        }
    }

    /** Borra las texturas descargadas. Para Ajustes. */
    fun clearCache() {
        memory.evictAll()
        runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
    }

    /** Cuánto ocupa la caché de mapas, para poder enseñarlo en Ajustes. */
    fun cacheBytes(): Long =
        runCatching { cacheDir.listFiles()?.sumOf { it.length() } ?: 0L }.getOrDefault(0L)

    private companion object {
        const val TILE = 256
        /** Descargas simultáneas. Doce casillas por zona; seis a la vez basta. */
        const val MAX_PARALLEL = 6

        /**
         * Lado máximo del mapa compuesto. Ningún móvil enseña más de 1080px de
         * ancho, así que 1440 deja margen para hacer zoom sin gastar memoria en
         * píxeles que nadie va a ver.
         */
        const val MAX_SIDE = 1440

        /** Presupuesto de la caché en memoria: unos seis mapas. */
        const val MEMORY_BUDGET_BYTES = 18 * 1024 * 1024
        const val TILE_SOURCE = "https://wago.tools/api/casc"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
    }
}
