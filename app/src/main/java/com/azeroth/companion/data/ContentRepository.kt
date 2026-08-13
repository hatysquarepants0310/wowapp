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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

data class InstanceSummary(
    val id: Int,
    val name: String,
    /**
     * Arte oficial de la instancia (el `tile` del Compendio de Aventuras).
     * Null si Blizzard no publica arte para esa instancia, que pasa con algunas
     * antiguas: en ese caso la tarjeta cae a color liso, nunca a un hueco.
     */
    val artUrl: String? = null,
)
data class Boss(val id: Int, val name: String)
data class ExpansionRef(val id: Int, val name: String, val isCurrent: Boolean)
data class ExpansionContent(
    val name: String,
    val dungeons: List<InstanceSummary>,
    val raids: List<InstanceSummary>,
)
data class Affix(
    val name: String,
    val description: String,
    /** URL del icono en la CDN oficial de Blizzard. Null si no vino nombre. */
    val iconUrl: String? = null,
)

/**
 * Icono del juego desde la CDN **oficial** de Blizzard.
 *
 * Se usa `render.worldofwarcraft.com` y no la CDN de Wowhead a propósito: este
 * proyecto no hace scraping de Wowhead (§0.2), y además esa es la fuente que
 * Blizzard publica para este uso. El tamaño 56 es el que sirve para una casilla
 * de 48dp sin verse borroso en pantallas de densidad alta.
 */
fun gameIconUrl(icon: String?): String? =
    icon?.takeIf { it.isNotBlank() }
        ?.let { "https://render.worldofwarcraft.com/us/icons/56/$it.jpg" }

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
    private val seasonLootRepository: SeasonLootRepository,
    okHttpClient: OkHttpClient,
    json: Json,
) {
    private val raiderIo: RaiderIoApi = Retrofit.Builder()
        .baseUrl("https://raider.io")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(RaiderIoApi::class.java)

    private val bossCache = mutableMapOf<Int, List<Boss>>()
    private val expansionCache = mutableMapOf<Int, ExpansionContent>()
    private var expansionsRefCache: List<ExpansionRef>? = null
    private val artCache = mutableMapOf<Int, String>()

    /** ID de expansión actual definido por el catálogo (Midnight por defecto). */
    suspend fun currentExpansionId(): Int = catalogRepository.load().journalExpansionId

    suspend fun currentAffixes(): Pair<String, List<Affix>>? {
        val region = settingsRepository.settings.first().region.name.lowercase()
        return runCatching {
            val dto = raiderIo.affixes(region)
            dto.title to dto.affix_details.map { it.toAffix() }
        }.getOrNull()
    }

    /**
     * El arte de una instancia, cacheado. `tile` es la ilustración ancha del
     * Compendio; si no viene, se usa cualquier otro asset antes que rendirse.
     */
    private suspend fun instanceArt(instanceId: Int): String? {
        artCache[instanceId]?.let { return it.ifBlank { null } }
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        val url = runCatching {
            val media = api.journalInstanceMedia(instanceId, region.namespaceStatic)
            media.assets.firstOrNull { it.key == "tile" }?.value
                ?: media.assets.firstOrNull()?.value
        }.getOrNull()
        artCache[instanceId] = url.orEmpty()
        return url
    }

    /** Todas las expansiones del juego; la actual marcada, el resto son "anteriores". */
    suspend fun allExpansions(): List<ExpansionRef> {
        expansionsRefCache?.let { return it }
        val region = settingsRepository.settings.first().region
        val currentId = currentExpansionId()
        val api = apiFactory.forRegion(region)
        return runCatching {
            api.journalExpansions(region.namespaceStatic).tiers
                // "Temporada actual" (505) es un agrupador meta duplicado: se omite.
                .filter { it.id != 505 && !it.name.isNullOrBlank() }
                .map { ExpansionRef(it.id, it.name!!, it.id == currentId) }
                .sortedByDescending { it.isCurrent }
                .also { expansionsRefCache = it }
        }.getOrDefault(emptyList())
    }

    suspend fun expansionContent(expansionId: Int): ExpansionContent? {
        expansionCache[expansionId]?.let { return it }
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        return runCatching {
            val exp = api.journalExpansion(expansionId, region.namespaceStatic)
            // El arte de cada instancia va en su propia petición, así que se
            // piden todas a la vez. Si una falla, esa tarjeta se queda sin arte
            // y las demás no se enteran: el arte es un adorno, no un requisito.
            coroutineScope {
                val dungeons = exp.dungeons.map { ref ->
                    async { InstanceSummary(ref.id, ref.name ?: "", instanceArt(ref.id)) }
                }
                val raids = exp.raids.map { ref ->
                    async { InstanceSummary(ref.id, ref.name ?: "", instanceArt(ref.id)) }
                }
                ExpansionContent(
                    name = exp.name,
                    dungeons = dungeons.map { it.await() },
                    raids = raids.map { it.await() },
                ).also { expansionCache[expansionId] = it }
            }
        }.getOrNull()
    }

    /** Jefes de una instancia (id + nombre, cacheado). Vacío si la API no responde. */
    suspend fun bosses(instanceId: Int): List<Boss> {
        bossCache[instanceId]?.let { return it }
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        return runCatching {
            api.journalInstance(instanceId, region.namespaceStatic)
                .encounters.filter { it.name != null }
                .map { Boss(it.id, it.name!!) }
        }.getOrDefault(emptyList()).also { bossCache[instanceId] = it }
    }

    /**
     * Botín de un jefe con imagen, calidad y probabilidad estimada. Responde
     * "¿qué looteo aquí y con qué chance?" para cualquier jefe del juego.
     */
    suspend fun bossLoot(encounterId: Int, isRaid: Boolean = true): List<LootEntry> =
        seasonLootRepository.bossLoot(encounterId, isRaid)

    private fun AffixDto.toAffix() = Affix(name, description, gameIconUrl(icon))
}
