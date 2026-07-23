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
                val token = runBlocking { authManager.validAccessToken(region) }
                val request = if (token != null) {
                    chain.request().newBuilder().header("Authorization", "Bearer $token").build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()
        Retrofit.Builder()
            .baseUrl(region.apiHost)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BlizzardApi::class.java)
    }
}
