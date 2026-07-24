package com.azeroth.companion.data

import com.azeroth.companion.core.catalog.CatalogRepository
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.network.AffixDto
import com.azeroth.companion.core.network.BlizzardApiFactory
import com.azeroth.companion.core.network.RaiderIoApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

data class InstanceSummary(val id: Int, val name: String)
data class ExpansionContent(
    val name: String,
    val dungeons: List<InstanceSummary>,
    val raids: List<InstanceSummary>,
)
data class Affix(val name: String, val description: String)

/**
 * Contenido de juego desde fuentes OFICIALES (§0.2, sin scraping de Wowhead):
 * - Blizzard Game Data (journal): mazmorras, bandas y jefes de la expansión.
 * - Raider.IO: afijos de Mythic+ de la semana.
 * Todo funciona con el token de aplicación, sin requerir sesión del usuario.
 */
@Singleton
class ContentRepository @Inject constructor(
    private val apiFactory: BlizzardApiFactory,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    okHttpClient: OkHttpClient,
    json: Json,
) {
    private val raiderIo: RaiderIoApi = Retrofit.Builder()
        .baseUrl("https://raider.io")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(RaiderIoApi::class.java)

    private val bossCache = mutableMapOf<Int, List<String>>()
    private var expansionCache: ExpansionContent? = null

    suspend fun currentAffixes(): Pair<String, List<Affix>>? {
        val region = settingsRepository.settings.first().region.name.lowercase()
        return runCatching {
            val dto = raiderIo.affixes(region)
            dto.title to dto.affix_details.map { it.toAffix() }
        }.getOrNull()
    }

    suspend fun expansionContent(): ExpansionContent? {
        expansionCache?.let { return it }
        val region = settingsRepository.settings.first().region
        val expansionId = catalogRepository.load().journalExpansionId
        val api = apiFactory.forRegion(region)
        return runCatching {
            val exp = api.journalExpansion(expansionId, region.namespaceStatic)
            ExpansionContent(
                name = exp.name,
                dungeons = exp.dungeons.map { InstanceSummary(it.id, it.name ?: "") },
                raids = exp.raids.map { InstanceSummary(it.id, it.name ?: "") },
            ).also { expansionCache = it }
        }.getOrNull()
    }

    /** Jefes de una instancia (cacheado). Lista vacía si la API no responde. */
    suspend fun bosses(instanceId: Int): List<String> {
        bossCache[instanceId]?.let { return it }
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        return runCatching {
            api.journalInstance(instanceId, region.namespaceStatic)
                .encounters.mapNotNull { it.name }
        }.getOrDefault(emptyList()).also { bossCache[instanceId] = it }
    }

    private fun AffixDto.toAffix() = Affix(name, description)
}
