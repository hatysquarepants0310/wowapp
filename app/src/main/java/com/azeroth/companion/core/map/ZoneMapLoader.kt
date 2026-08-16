package com.azeroth.companion.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.collection.LruCache
import com.azeroth.companion.core.datastore.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Cuadrícula de tiles de una zona y su tamaño real. */
@Serializable
data class MapTileGrid(
    val r: Int = 0,
    val c: Int = 0,
    val w: Int = 0,
    val h: Int = 0,
    val t: List<Int> = emptyList(),
)

/** Resultado de pedir el arte de una zona. El UI no tiene que adivinar un null. */
sealed class ZoneMapLoadResult {
    data class Ready(val bitmap: Bitmap) : ZoneMapLoadResult()
    data object Skipped : ZoneMapLoadResult()
    data class Failed(val reason: ZoneMapFailReason) : ZoneMapLoadResult()
}

enum class ZoneMapFailReason {
    TILE,
}

/**
 * Compone el mapa REAL de una zona del juego.
 *
 * El APK no lleva arte de Blizzard dentro: solo el índice de qué archivos
 * componen cada zona (`map_tiles.json`, números). La primera pintura es
 * UN JPEG de zamimg (timeout corto). Las texturas BLP refinan detrás,
 * se decodifican y quedan en `.blp` + `.azt1`. Si el JPEG no llega a
 * tiempo, error explícito: nunca una leyenda eterna.
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

    private val decodedTiles: DecodedTileCache by lazy {
        DecodedTileCache(cacheDir)
    }

    /** Timeout corto y cancelable: wago no puede dejar el placeholder colgado. */
    private val previewClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(ZoneMapFirstPaint.TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
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
            .filterNot { File(cacheDir, "$it.blp").let { f -> f.exists() && f.length() > 64 && isBlp(f) } }
        if (pending.isEmpty()) return
        withContext(Dispatchers.IO) {
            val gate = Semaphore(MAX_PARALLEL)
            coroutineScope {
                pending.forEach { fileId ->
                    launch {
                        gate.withPermit {
                            downloadBlp(fileId)?.let { data ->
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
     *
     * Preferir [loadResult] / [loadProgress]: un null no dice si sigue
     * cargando, si el arte está apagado o si el tile reventó.
     */
    suspend fun load(uiMapId: Int): Bitmap? =
        (loadResult(uiMapId) as? ZoneMapLoadResult.Ready)?.bitmap

    /**
     * Último resultado de [loadProgress]. La primera pintura puede ser
     * placeholder o error; los pines no esperan a esto.
     */
    suspend fun loadResult(uiMapId: Int): ZoneMapLoadResult {
        var last: ZoneMapLoadResult = ZoneMapLoadResult.Failed(ZoneMapFailReason.TILE)
        loadProgress(uiMapId).collect { last = it }
        return last
    }

    /**
     * Emite el mapa a medida que hay algo que pintar.
     *
     * Primero UN JPEG de zamimg (timeout [ZoneMapFirstPaint.TIMEOUT_MS]).
     * El BLP (`.azt1` / red) refina detrás. Si no hay arte a tiempo,
     * [ZoneMapLoadResult.Failed] — nunca un placeholder eterno.
     */
    fun loadProgress(uiMapId: Int): Flow<ZoneMapLoadResult> = flow {
        memory.get(uiMapId)?.let {
            emit(ZoneMapLoadResult.Ready(it))
            return@flow
        }
        if (!settingsRepository.settings.first().downloadMapArt) {
            emit(ZoneMapLoadResult.Skipped)
            return@flow
        }
        val jpeg = withTimeoutOrNull(ZoneMapFirstPaint.TIMEOUT_MS) {
            withContext(Dispatchers.IO) { jpegPreview(uiMapId) }
        }
        if (jpeg != null) {
            memory.put(uiMapId, jpeg)
            emit(ZoneMapLoadResult.Ready(jpeg))
        }
        val grid = grids()[uiMapId]
        val blpBudget = if (jpeg != null) BLP_REFINE_MS else ZoneMapFirstPaint.TIMEOUT_MS
        var lastPartial: Bitmap? = null
        val composed = if (grid != null) {
            withTimeoutOrNull(blpBudget) {
                try {
                    compose(grid) { partial ->
                        lastPartial = partial
                        memory.put(uiMapId, partial)
                        emit(ZoneMapLoadResult.Ready(partial))
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    null
                }
            }
        } else {
            null
        }
        val bitmap = composed ?: lastPartial ?: jpeg
        if (bitmap == null) {
            emit(ZoneMapLoadResult.Failed(ZoneMapFailReason.TILE))
            return@flow
        }
        memory.put(uiMapId, bitmap)
        if (composed != null && composed !== lastPartial) {
            emit(ZoneMapLoadResult.Ready(bitmap))
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
    private suspend fun compose(
        grid: MapTileGrid,
        onPartial: (suspend (Bitmap) -> Unit)? = null,
    ): Bitmap? = coroutineScope {
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
        var drawn = 0

        // POR TANDAS, no todas de golpe — y las que ya están en `.azt1`
        // primero, para que el placeholder caiga sin esperar a la red.
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
        val order = ProgressiveTiles.schedule(grid.t) { fileId ->
            decodedTiles.fileFor(fileId).exists()
        }
        order.chunked(MAX_PARALLEL).forEach { chunk ->
            val decoded = chunk.map { indexed ->
                async(Dispatchers.IO) { indexed.index to tile(indexed.value) }
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
                drawn++
            }

            val remaining = grid.t.size - drawn - missing
            if (ProgressiveTiles.publish(drawn, missing, remaining) == ProgressiveTiles.Publish.PARTIAL) {
                onPartial?.invoke(out.copy(out.config ?: Bitmap.Config.RGB_565, false))
            }
        }

        when (ProgressiveTiles.publish(drawn, missing, remaining = 0)) {
            ProgressiveTiles.Publish.FAILED -> {
                out.recycle()
                null
            }
            else -> out
        }
    }

    /**
     * Primero el bitmap ya decodificado (`fileId.azt1`). El `.blp` en disco
     * evita la red, pero re-decodificar DXT en cada apertura era lo que
     * dejaba el mapa en blanco. Si el `.azt1` falta o está corrupto se
     * vuelve al BLP (disco o red) y se reescribe la caché.
     */
    private suspend fun tile(fileId: Int): Bitmap? {
        decodedTiles.read(fileId)?.let { return bitmapFrom(it) }

        val cached = File(cacheDir, "$fileId.blp")
        if (cached.exists() && !isBlp(cached)) cached.delete()
        val bytes = if (cached.exists() && cached.length() > 64) {
            runCatching { cached.readBytes() }.getOrNull()
        } else {
            downloadBlp(fileId)?.also { data ->
                if (isBlp(data)) runCatching { cached.writeBytes(data) }
            }
        } ?: return null
        val pixels = BlpDecoder.decodePixels(bytes) ?: return null
        decodedTiles.write(fileId, pixels)
        return bitmapFrom(pixels)
    }

    private fun bitmapFrom(pixels: BlpDecoder.Pixels): Bitmap =
        Bitmap.createBitmap(pixels.argb, pixels.width, pixels.height, Bitmap.Config.ARGB_8888)

    private suspend fun jpegPreview(uiMapId: Int): Bitmap? {
        val cached = File(cacheDir, "$uiMapId.jpg")
        val bytes = if (cached.exists() && cached.length() > 1_000) {
            runCatching { cached.readBytes() }.getOrNull()
        } else {
            downloadUrl("$JPEG_SOURCE/$uiMapId.jpg")?.also { data ->
                if (data.size > 1_000 && data[0] == 0xFF.toByte()) {
                    runCatching { cached.writeBytes(data) }
                }
            }
        } ?: return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }

    private suspend fun downloadBlp(fileId: Int): ByteArray? =
        downloadUrl("$TILE_SOURCE/$fileId?download")?.takeIf(::isBlp)

    private suspend fun downloadUrl(url: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .build()
        val call = previewClient.newCall(request)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val bytes = response.use {
                        if (!it.isSuccessful) null else it.body?.bytes()
                    }
                    if (cont.isActive) cont.resume(bytes)
                }
            })
        }
    }

    private fun isBlp(file: File): Boolean = runCatching {
        val head = ByteArray(4)
        file.inputStream().use { it.read(head) }
        isBlp(head)
    }.getOrDefault(false)

    private fun isBlp(data: ByteArray): Boolean =
        data.size >= 4 &&
            data[0] == 'B'.code.toByte() &&
            data[1] == 'L'.code.toByte() &&
            data[2] == 'P'.code.toByte() &&
            data[3] == '2'.code.toByte()

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
        /** BLP de refinamiento detrás del JPEG; no puede bloquear el primer paint. */
        const val BLP_REFINE_MS = 45_000L
        const val TILE_SOURCE = "https://wago.tools/api/casc"
        const val JPEG_SOURCE = "https://wow.zamimg.com/images/wow/maps/enus/original"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
    }
}
