package com.azeroth.companion.core.map

/**
 * Orden y plazo del primer paint del mapa.
 *
 * Lo lento: 12–150 BLP de wago en serie, y si compose devolvía null
 * (missing>1) el JPEG de zamimg ni se tocaba. El placeholder se quedaba.
 *
 * Un JPEG primero (un archivo). BLP solo refina detrás. Si el timeout
 * corto se agota sin arte, error explícito — nunca leyenda eterna.
 */
object ZoneMapFirstPaint {

    const val TIMEOUT_MS = 8_000L
    const val firstSourceIsSingleFile = true

    enum class Source { JPEG, BLP }

    sealed class Decision {
        data object KeepWaiting : Decision()
        data object ShowJpeg : Decision()
        data object ShowBlp : Decision()
        data object Fail : Decision()
    }

    fun sources(): List<Source> = listOf(Source.JPEG, Source.BLP)

    fun decide(
        elapsedMs: Long,
        jpegReady: Boolean,
        blpReady: Boolean,
        timeoutMs: Long = TIMEOUT_MS,
    ): Decision = when {
        blpReady -> Decision.ShowBlp
        jpegReady -> Decision.ShowJpeg
        elapsedMs >= timeoutMs -> Decision.Fail
        else -> Decision.KeepWaiting
    }

    fun loadingMapsAfterAttempt(finished: Boolean, threw: Boolean): Boolean =
        !(finished || threw)
}
