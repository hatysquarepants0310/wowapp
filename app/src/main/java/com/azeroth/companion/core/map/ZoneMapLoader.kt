package com.azeroth.companion.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
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

    // Un mapa compuesto de zona ronda los 2,6 MB en memoria. Tres es suficiente
    // para moverse entre zonas sin recomponer y sin inflar la app.
    private val memory = object : LruCache<Int, Bitmap>(3) {
        override fun sizeOf(key: Int, value: Bitmap) = 1
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
        return runCatching { compose(uiMapId, grid) }.getOrNull()?.also {
            memory.put(uiMapId, it)
        }
    }

    private suspend fun compose(uiMapId: Int, grid: MapTileGrid): Bitmap? = coroutineScope {
        val tiles = grid.t.mapIndexed { index, fileId ->
            async(Dispatchers.IO) { index to tile(fileId) }
        }.map { it.await() }

        // Si falta más de una casilla el mapa saldría con agujeros: mejor nada.
        if (tiles.count { it.second == null } > 1) return@coroutineScope null

        val full = Bitmap.createBitmap(grid.c * TILE, grid.r * TILE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(full)
        tiles.forEach { (index, bitmap) ->
            if (bitmap == null) return@forEach
            val row = index / grid.c
            val col = index % grid.c
            canvas.drawBitmap(bitmap, (col * TILE).toFloat(), (row * TILE).toFloat(), null)
            bitmap.recycle()
        }

        // Recorte al tamaño real del mapa: la cuadrícula sobra por abajo y por
        // la derecha, y ese sobrante desplazaría cada punto de misión.
        val width = grid.w.coerceIn(1, full.width)
        val height = grid.h.coerceIn(1, full.height)
        if (width == full.width && height == full.height) return@coroutineScope full
        val cropped = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(cropped).drawBitmap(
            full, Rect(0, 0, width, height), Rect(0, 0, width, height), null,
        )
        full.recycle()
        cropped
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
        const val TILE_SOURCE = "https://wago.tools/api/casc"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
    }
}
