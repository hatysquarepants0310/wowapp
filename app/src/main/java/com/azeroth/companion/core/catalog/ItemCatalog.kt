package com.azeroth.companion.core.catalog

import android.content.Context
import com.azeroth.companion.core.datastore.LanguagePref
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Calidad de un objeto, en el orden y los colores del juego. */
enum class ItemQuality { POOR, COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, ARTIFACT, HEIRLOOM }

/**
 * Nombres y calidad de los objetos, horneados en el APK.
 *
 * La API de subastas solo devuelve IDs, y resolverlos en caliente serían
 * decenas de miles de peticiones por actualización. El índice cubre los objetos
 * que aparecen de verdad en la casa de subastas (ver `tools/build_items.py`);
 * lo que falte se muestra por su ID en lugar de inventarse un nombre.
 *
 * El español del juego solo traduce una parte del catálogo, así que cuando un
 * objeto no tiene nombre en español se cae al inglés: es exactamente lo que
 * hace el cliente del juego.
 */
@Singleton
class ItemCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var names: Map<Int, String>? = null
    private var fallbackNames: Map<Int, String>? = null
    private var qualities: Map<Int, Int>? = null

    suspend fun ensureLoaded() {
        if (names != null) return
        mutex.withLock {
            if (names != null) return
            val spanish = (LanguagePref.read(context) ?: java.util.Locale.getDefault().language)
                .startsWith("es")
            withContext(Dispatchers.IO) {
                fallbackNames = readNames("catalog/items_en.json")
                names = if (spanish) readNames("catalog/items_es.json") else fallbackNames
                qualities = readMap("catalog/item_quality.json")
            }
        }
    }

    suspend fun name(itemId: Int): String? {
        ensureLoaded()
        return names?.get(itemId) ?: fallbackNames?.get(itemId)
    }

    suspend fun quality(itemId: Int): ItemQuality? {
        ensureLoaded()
        return qualities?.get(itemId)?.let { ItemQuality.entries.getOrNull(it) }
    }

    /** Objetos cuyo nombre contiene [query], para el buscador de la casa. */
    suspend fun search(query: String, limit: Int = 60): List<Int> {
        ensureLoaded()
        val needle = query.trim().lowercase()
        if (needle.length < 2) return emptyList()
        val source = names ?: return emptyList()
        val exact = ArrayList<Int>()
        val partial = ArrayList<Int>()
        for ((id, name) in source) {
            val lower = name.lowercase()
            when {
                lower.startsWith(needle) -> exact += id
                lower.contains(needle) -> partial += id
            }
            if (exact.size >= limit) break
        }
        return (exact + partial).take(limit)
    }

    private fun readNames(asset: String): Map<Int, String> = runCatching {
        context.assets.open(asset).bufferedReader().use { reader ->
            json.decodeFromString(
                MapSerializer(String.serializer(), String.serializer()), reader.readText(),
            ).mapKeys { it.key.toInt() }
        }
    }.getOrDefault(emptyMap())

    private fun readMap(asset: String): Map<Int, Int> = runCatching {
        context.assets.open(asset).bufferedReader().use { reader ->
            json.decodeFromString(
                MapSerializer(String.serializer(), Int.serializer()), reader.readText(),
            ).mapKeys { it.key.toInt() }
        }
    }.getOrDefault(emptyMap())
}

/** Formatea cobre como oro/plata/cobre, igual que el juego. */
fun formatGold(copper: Long): String {
    val gold = copper / 10_000
    val silver = (copper % 10_000) / 100
    val rest = copper % 100
    return when {
        gold > 0 -> "%,d".format(gold) + "o " + "%02d".format(silver) + "p"
        silver > 0 -> "${silver}p ${rest}c"
        else -> "${rest}c"
    }
}
