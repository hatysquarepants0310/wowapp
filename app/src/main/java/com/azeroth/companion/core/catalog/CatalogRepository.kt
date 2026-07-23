package com.azeroth.companion.core.catalog

import android.content.Context
import com.azeroth.companion.core.model.Region
import com.azeroth.companion.core.time.ResetRules
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente del catálogo: copia embebida en assets como fallback garantizado,
 * más una copia descargada opcional en el almacenamiento interno. Si la
 * remota falla o está corrupta, la embebida siempre gana (§0.2: la app
 * nunca muere por dependencia externa).
 */
@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {

    private val _catalog = MutableStateFlow<Catalog?>(null)
    val catalog: StateFlow<Catalog?> = _catalog

    /** "embedded" o "downloaded", para la pantalla de diagnóstico (§11). */
    var activeSource: String = "embedded"
        private set

    suspend fun load(): Catalog = withContext(Dispatchers.IO) {
        _catalog.value?.let { return@withContext it }
        val embedded = parse(context.assets.open(EMBEDDED_PATH).bufferedReader().readText())
        val downloaded = downloadedFile().takeIf { it.exists() }?.let {
            runCatching { parse(it.readText()) }.getOrNull()
        }
        val chosen = if (downloaded != null && downloaded.catalogVersion > embedded.catalogVersion) {
            activeSource = "downloaded"
            downloaded
        } else {
            activeSource = "embedded"
            embedded
        }
        _catalog.value = chosen
        chosen
    }

    /** Guarda un catálogo remoto ya validado. Se aplica en el próximo load. */
    suspend fun storeDownloaded(raw: String): Boolean = withContext(Dispatchers.IO) {
        val parsed = runCatching { parse(raw) }.getOrNull() ?: return@withContext false
        val current = _catalog.value
        if (current != null && parsed.catalogVersion <= current.catalogVersion) return@withContext false
        downloadedFile().writeText(raw)
        activeSource = "downloaded"
        _catalog.value = parsed
        true
    }

    fun resetRulesFor(region: Region): ResetRules? =
        _catalog.value?.resets?.firstOrNull { it.region == region }

    private fun parse(raw: String): Catalog = json.decodeFromString(Catalog.serializer(), raw)

    private fun downloadedFile() = File(context.filesDir, "catalog/catalog.json").apply {
        parentFile?.mkdirs()
    }

    companion object {
        const val EMBEDDED_PATH = "catalog/catalog.json"
    }
}
