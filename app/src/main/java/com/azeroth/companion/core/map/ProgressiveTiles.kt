package com.azeroth.companion.core.map

/**
 * Orden y cuándo pintar durante la carga del mapa.
 *
 * Lo lento era esperar a componer TODAS las casillas (red + DXT) antes
 * de enseñar la primera. Aquí las que ya están en `.azt1` van primero;
 * el placeholder se queda hasta el primer tile listo; si fallan
 * demasiadas casillas el resultado es error, no un plano blanco.
 */
object ProgressiveTiles {

    const val allowsPlaceholderUntilFirstTile = true

    enum class Publish {
        HOLD,
        PARTIAL,
        READY,
        FAILED,
    }

    fun schedule(
        fileIds: List<Int>,
        cached: (Int) -> Boolean,
    ): List<IndexedValue<Int>> {
        val indexed = fileIds.withIndex().toList()
        return indexed.filter { cached(it.value) } + indexed.filter { !cached(it.value) }
    }

    fun publish(drawn: Int, missing: Int, remaining: Int): Publish = when {
        remaining > 0 && drawn == 0 -> Publish.HOLD
        remaining > 0 -> Publish.PARTIAL
        missing > 1 -> Publish.FAILED
        drawn == 0 -> Publish.FAILED
        else -> Publish.READY
    }
}
