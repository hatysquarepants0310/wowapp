package com.azeroth.companion.data

import com.azeroth.companion.core.catalog.ItemCatalog
import com.azeroth.companion.core.catalog.ItemQuality
import com.azeroth.companion.core.database.AuctionPriceDao
import com.azeroth.companion.core.database.AuctionPriceEntity
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.network.AuctionDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Una fila de la casa de subastas ya lista para pintar. */
data class AuctionListing(
    val itemId: Int,
    val name: String,
    val quality: ItemQuality?,
    val minUnitPrice: Long,
    val quantity: Long,
    val listings: Int,
)

/** Qué mercado se está mirando. */
enum class AuctionScope { COMMODITIES, REALM }

data class AuctionStatus(
    val updatedAt: Instant?,
    val items: Int,
    val stale: Boolean,
)

/**
 * Casa de subastas.
 *
 * Blizzard publica dos volcados distintos y no intercambiables: las mercancías
 * (consumibles y materiales, con precio único para toda la región) y las
 * subastas de cada reino conectado (equipo, monturas, recetas). La app los
 * guarda por separado porque el mismo objeto no tiene por qué costar lo mismo
 * en los dos, y presentarlos juntos daría un precio que no existe en ninguno.
 *
 * Los volcados se regeneran una vez por hora, así que la caché vale una hora;
 * pedirlos más a menudo solo gastaría los megas del usuario para nada.
 */
@Singleton
class AuctionRepository @Inject constructor(
    private val downloader: AuctionDownloader,
    private val dao: AuctionPriceDao,
    private val itemCatalog: ItemCatalog,
    private val settingsRepository: SettingsRepository,
    private val characterRepository: CharacterRepositoryPort,
) {

    suspend fun status(scope: AuctionScope): AuctionStatus {
        val key = scopeKey(scope)
        val updatedAt = dao.updatedAt(key)
        return AuctionStatus(
            updatedAt = updatedAt,
            items = dao.count(key),
            stale = updatedAt == null ||
                Duration.between(updatedAt, Instant.now()) > CACHE_TTL,
        )
    }

    /** Descarga y agrega el volcado. Devuelve cuántos objetos quedaron. */
    suspend fun refresh(scope: AuctionScope): Result<Int> = runCatching {
        val region = settingsRepository.settings.first().region
        val prices = withContext(Dispatchers.IO) {
            when (scope) {
                AuctionScope.COMMODITIES -> downloader.commodities(region)
                AuctionScope.REALM -> {
                    val realmId = characterRepository.activeConnectedRealmId()
                        ?: error("Sin personaje activo: no se sabe qué reino consultar.")
                    downloader.connectedRealm(region, realmId)
                }
            }
        }
        val key = scopeKey(scope)
        val now = Instant.now()
        dao.clearScope(key)
        // En trozos: 25.000 filas en una sola sentencia revienta el límite de
        // variables de SQLite.
        prices.chunked(500).forEach { chunk ->
            dao.upsertAll(
                chunk.map {
                    AuctionPriceEntity(
                        scope = key,
                        itemId = it.itemId,
                        minUnitPrice = it.minUnitPrice,
                        quantity = it.quantity,
                        listings = it.listings,
                        updatedAt = now,
                    )
                },
            )
        }
        prices.size
    }

    suspend fun search(scope: AuctionScope, query: String): List<AuctionListing> {
        val ids = itemCatalog.search(query)
        if (ids.isEmpty()) return emptyList()
        return decorate(dao.forItems(scopeKey(scope), ids))
            .sortedBy { it.minUnitPrice }
    }

    /** Lo más caro del mercado: es lo que la gente quiere ver primero. */
    suspend fun mostExpensive(scope: AuctionScope, limit: Int = 25): List<AuctionListing> =
        decorate(dao.mostExpensive(scopeKey(scope), limit))

    /** Lo que más se mueve: el pulso real de la economía del servidor. */
    suspend fun mostTraded(scope: AuctionScope, limit: Int = 25): List<AuctionListing> =
        decorate(dao.mostTraded(scopeKey(scope), limit))

    private suspend fun decorate(rows: List<AuctionPriceEntity>): List<AuctionListing> {
        itemCatalog.ensureLoaded()
        return rows.map { row ->
            AuctionListing(
                itemId = row.itemId,
                // Sin nombre horneado se muestra el ID: es feo pero es cierto,
                // y prefiero eso a esconder el objeto o inventarle un nombre.
                name = itemCatalog.name(row.itemId) ?: "#${row.itemId}",
                quality = itemCatalog.quality(row.itemId),
                minUnitPrice = row.minUnitPrice,
                quantity = row.quantity,
                listings = row.listings,
            )
        }
    }

    private fun scopeKey(scope: AuctionScope) = when (scope) {
        AuctionScope.COMMODITIES -> COMMODITIES_SCOPE
        AuctionScope.REALM -> REALM_SCOPE
    }

    private companion object {
        const val COMMODITIES_SCOPE = 0
        /**
         * Las subastas de reino se guardan siempre bajo la misma clave y se
         * borran al refrescar: el usuario mira un reino cada vez y guardar 83
         * mercados sería llenarle el móvil sin motivo.
         */
        const val REALM_SCOPE = 1
        val CACHE_TTL: Duration = Duration.ofHours(1)
    }
}

/**
 * Lo justo que la casa de subastas necesita saber del roster. Se declara aquí
 * para no arrastrar todo [RosterRepository] a la capa de economía.
 */
interface CharacterRepositoryPort {
    suspend fun activeConnectedRealmId(): Int?
}
