package com.azeroth.companion.core.map

/**
 * Qué hay que pintar en la superficie del mapa.
 *
 * El bitmap tarda: descargar BLP, decodificar, componer. Si la primera
 * composición espera a ese bitmap, el usuario ve un plano vacío. Por eso
 * el estado por defecto —sin bitmap y sin un fallo ya conocido— es
 * [Loading], nunca un lienzo en blanco.
 */
sealed class ZoneMapArtState {
    data object Loading : ZoneMapArtState()
    data object Ready : ZoneMapArtState()
    /** Arte desactivado o no aplicable: se queda la rejilla, no un error. */
    data object Fallback : ZoneMapArtState()
    data class Failed(val message: String) : ZoneMapArtState()

    companion object {
        fun resolve(
            hasBitmap: Boolean,
            failedMessage: String?,
            fallback: Boolean = false,
        ): ZoneMapArtState = when {
            hasBitmap -> Ready
            failedMessage != null -> Failed(failedMessage)
            fallback -> Fallback
            else -> Loading
        }
    }
}
