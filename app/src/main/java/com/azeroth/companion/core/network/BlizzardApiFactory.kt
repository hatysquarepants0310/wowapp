package com.azeroth.companion.core.network

import com.azeroth.companion.core.model.Region
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Construye [BlizzardApi] por región con el token OAuth inyectado por
 * interceptor. Si no hay token, la petición sale sin Authorization y la API
 * responderá 401: el llamador lo trata como modo degradado, nunca como crash.
 */
@Singleton
class BlizzardApiFactory @Inject constructor(
    private val baseClient: OkHttpClient,
    private val authManager: AuthManager,
    private val json: Json,
) {
    private val cache = mutableMapOf<Region, BlizzardApi>()

    fun forRegion(region: Region): BlizzardApi = cache.getOrPut(region) {
        val client = baseClient.newBuilder()
            .addInterceptor { chain ->
                // Solo /profile/user/** (el roster de la CUENTA) exige el token del
                // usuario. Todo lo demás —incluidos /profile/wow/character/**, que es
                // de donde sale el progreso— funciona con el token de aplicación, que
                // la app se emite a sí misma. Verificado contra la API: misiones
                // completadas, bandas, M+ y estadísticas responden 200 con él.
                // Gracias a eso, que la sesión de Battle.net caduque ya no interrumpe
                // la sincronización.
                val needsUser = chain.request().url.encodedPath.startsWith("/profile/user")
                val user = runBlocking { authManager.validAccessToken(region) }
                val token = if (needsUser) user else {
                    user ?: runBlocking { authManager.appAccessToken(region) }
                }
                var response = chain.proceed(chain.request().withToken(token))
                // Un 401 con el token del usuario (revocado, caducado justo ahora)
                // no debe dejar la pantalla vacía si el de aplicación sí sirve.
                if (response.code == 401 && !needsUser && token == user) {
                    val app = runBlocking { authManager.appAccessToken(region) }
                    if (app != null && app != token) {
                        response.close()
                        response = chain.proceed(chain.request().withToken(app))
                    }
                }
                response
            }
            .build()
        Retrofit.Builder()
            .baseUrl(region.apiHost)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BlizzardApi::class.java)
    }

    private fun okhttp3.Request.withToken(token: String?) =
        if (token == null) this else newBuilder().header("Authorization", "Bearer $token").build()
}
