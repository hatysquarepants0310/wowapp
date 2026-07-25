package com.azeroth.companion.data

import android.content.Context
import com.azeroth.companion.core.database.CharacterDao
import com.azeroth.companion.core.database.CharacterEntity
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.network.BlizzardApiFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class EquippedItem(
    val slot: String,
    val name: String,
    val itemLevel: Int,
    val quality: String,
    val iconUrl: String? = null,
)

data class MountEntry(val id: Int, val name: String, val imageUrl: String?)

data class CharacterDetail(
    val equipment: List<EquippedItem>,
    val mountCount: Int,
    val mounts: List<MountEntry>,
)

@kotlinx.serialization.Serializable
private data class MountsFile(
    val renderPattern: String = "",
    val displays: Map<String, Int> = emptyMap(),
)

@Singleton
class CharacterDetailRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiFactory: BlizzardApiFactory,
    private val characterDao: CharacterDao,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
) {
    private val iconCache = mutableMapOf<Int, String?>()

    fun roster(): Flow<List<CharacterEntity>> = characterDao.observeAll()

    /** Equipo por slot y colección de monturas del personaje (requiere sesión). */
    suspend fun detail(character: CharacterEntity): CharacterDetail {
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        val ns = region.namespaceProfile
        val realm = character.realmSlug
        val name = character.name.lowercase()

        val equipment = runCatching {
            api.equipment(realm, name, ns).equipped_items.map { dto ->
                // Icono real del objeto vía media (datos estáticos, token de app).
                val iconUrl = dto.item?.id?.let { itemId ->
                    iconCache.getOrPut(itemId) {
                        runCatching {
                            val assets = api.itemMedia(itemId, region.namespaceStatic).assets
                            assets.firstOrNull { it.key == "icon" }?.value
                                ?: assets.firstOrNull()?.value
                        }.getOrNull()
                    }
                }
                EquippedItem(
                    slot = dto.slot?.name ?: "",
                    name = dto.name ?: "",
                    itemLevel = dto.level?.value ?: 0,
                    quality = dto.quality?.name ?: "",
                    iconUrl = iconUrl,
                )
            }
        }.getOrDefault(emptyList())

        // Imagen de cada montura sin llamadas extra a la API: el dataset horneado
        // da el creature display y la URL de render se construye por patrón.
        val displays = mountDisplays()
        val mounts = runCatching {
            api.mountsNamed(realm, name, ns).mounts.mapNotNull { entry ->
                val id = entry.mount.id
                val mountName = entry.mount.name ?: return@mapNotNull null
                MountEntry(
                    id = id,
                    name = mountName,
                    imageUrl = displays.second[id.toString()]?.let {
                        displays.first.replace("{id}", it.toString())
                    },
                )
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())

        return CharacterDetail(equipment, mounts.size, mounts)
    }

    /** (patrón de render, mapa mountId -> creatureDisplayId) del dataset local. */
    private suspend fun mountDisplays(): Pair<String, Map<String, Int>> =
        withContext(Dispatchers.IO) {
            mountsCache?.let { return@withContext it }
            runCatching {
                val raw = context.assets.open("catalog/mounts.json").bufferedReader().readText()
                val file = json.decodeFromString(MountsFile.serializer(), raw)
                file.renderPattern to file.displays
            }.getOrDefault("" to emptyMap()).also { mountsCache = it }
        }

    private var mountsCache: Pair<String, Map<String, Int>>? = null
}
