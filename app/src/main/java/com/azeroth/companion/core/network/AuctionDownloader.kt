package com.azeroth.companion.core.network

import android.util.JsonReader
import com.azeroth.companion.core.model.Region
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** Precio agregado de un objeto en la casa de subastas. */
data class AuctionPrice(
    val itemId: Int,
    /** Precio unitario más barato, en cobre. */
    val minUnitPrice: Long,
    /** Unidades totales a la venta. */
    val quantity: Long,
    /** Cuántas subastas distintas hay de ese objeto. */
    val listings: Int,
)

/**
 * Descarga y agrega la casa de subastas.
 *
 * El volcado de mercancías de una región son ~24 MB y 251.000 pujas; el de un
 * reino conectado grande, otros 12 MB. Deserializarlo a objetos costaría cientos
 * de megas de memoria y mataría la app en un móvil modesto, así que se lee en
 * streaming con [JsonReader] y solo se guarda el agregado: unos 10.000 objetos
 * con su precio mínimo. De 24 MB de red se pasa a unos cientos de kB en disco.
 *
 * Blizzard regenera estos volcados una vez por hora, así que no tiene sentido
 * pedirlos más a menudo.
 */
@Singleton
class AuctionDownloader @Inject constructor(
    private val client: OkHttpClient,
    private val authManager: AuthManager,
) {

    /** Mercancías: consumibles y materiales, con precio único para toda la región. */
    suspend fun commodities(region: Region): List<AuctionPrice> =
        fetch(region, "${region.apiHost}/data/wow/auctions/commodities?namespace=${region.namespaceDynamic}")

    /** Subastas de un reino conectado: equipo y todo lo que no es mercancía. */
    suspend fun connectedRealm(region: Region, connectedRealmId: Int): List<AuctionPrice> = fetch(
        region,
        "${region.apiHost}/data/wow/connected-realm/$connectedRealmId/auctions" +
            "?namespace=${region.namespaceDynamic}",
    )

    private suspend fun fetch(region: Region, url: String): List<AuctionPrice> {
        val token = authManager.appAccessToken(region)
            ?: authManager.validAccessToken(region)
            ?: error("Sin token para la casa de subastas")
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} en la casa de subastas")
            val body = response.body ?: error("Respuesta vacía de la casa de subastas")
            return body.charStream().use { reader -> aggregate(JsonReader(reader)) }
        }
    }

    /**
     * Recorre `{"auctions":[{item:{id},unit_price|buyout|bid,quantity},…]}` sin
     * materializar la lista. `unit_price` solo existe en mercancías; el equipo
     * usa `buyout`, y si alguien listó sin compra directa queda `bid`, que es lo
     * único que se puede afirmar de esa subasta.
     */
    internal fun aggregate(reader: JsonReader): List<AuctionPrice> {
        val minPrice = HashMap<Int, Long>()
        val quantities = HashMap<Int, Long>()
        val listings = HashMap<Int, Int>()

        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() != "auctions") {
                reader.skipValue()
                continue
            }
            reader.beginArray()
            while (reader.hasNext()) {
                var itemId = 0
                var price = 0L
                var quantity = 1L
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "item" -> {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                if (reader.nextName() == "id") itemId = reader.nextInt()
                                else reader.skipValue()
                            }
                            reader.endObject()
                        }
                        "unit_price" -> price = reader.nextLong()
                        "buyout" -> if (price == 0L) price = reader.nextLong() else reader.skipValue()
                        "bid" -> if (price == 0L) price = reader.nextLong() else reader.skipValue()
                        "quantity" -> quantity = reader.nextLong()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (itemId != 0 && price > 0) {
                    // El precio de una subasta de equipo es por lote, no por
                    // unidad: dividir da el mismo "por unidad" que en mercancías
                    // y permite comparar sin mezclar dos escalas distintas.
                    val unit = if (quantity > 1) price / quantity else price
                    val previous = minPrice[itemId]
                    if (previous == null || unit < previous) minPrice[itemId] = unit
                    quantities[itemId] = (quantities[itemId] ?: 0L) + quantity
                    listings[itemId] = (listings[itemId] ?: 0) + 1
                }
            }
            reader.endArray()
        }
        reader.endObject()

        return minPrice.map { (itemId, price) ->
            AuctionPrice(
                itemId = itemId,
                minUnitPrice = price,
                quantity = quantities[itemId] ?: 0L,
                listings = listings[itemId] ?: 0,
            )
        }
    }
}
