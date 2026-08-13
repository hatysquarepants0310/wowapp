package com.azeroth.companion.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API pública y gratuita de Raider.IO (sin autenticación). Fuente oficial para
 * los afijos de Mythic+ de la semana con descripciones localizadas.
 */
interface RaiderIoApi {

    @GET("/api/v1/mythic-plus/affixes")
    suspend fun affixes(
        @Query("region") region: String,
        @Query("locale") locale: String = "es",
    ): AffixesDto
}

@Serializable
data class AffixesDto(
    val title: String = "",
    val affix_details: List<AffixDto> = emptyList(),
)

@Serializable
data class AffixDto(
    val name: String = "",
    val description: String = "",
    /**
     * Nombre del icono del juego, p. ej. `spell_shadow_shadowfiend`. Raider.IO
     * lo devuelve y hasta ahora se estaba tirando: por eso los afijos salían
     * como párrafos de texto sin una sola imagen, que es lo que hacía que la
     * pantalla se leyera como una wiki y no como algo de World of Warcraft.
     */
    val icon: String = "",
)
