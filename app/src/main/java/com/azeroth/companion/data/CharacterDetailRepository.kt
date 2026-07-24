package com.azeroth.companion.data

import com.azeroth.companion.core.database.CharacterDao
import com.azeroth.companion.core.database.CharacterEntity
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.network.BlizzardApiFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class EquippedItem(val slot: String, val name: String, val itemLevel: Int, val quality: String)
data class CharacterDetail(
    val equipment: List<EquippedItem>,
    val mountCount: Int,
    val mountNames: List<String>,
)

@Singleton
class CharacterDetailRepository @Inject constructor(
    private val apiFactory: BlizzardApiFactory,
    private val characterDao: CharacterDao,
    private val settingsRepository: SettingsRepository,
) {
    fun roster(): Flow<List<CharacterEntity>> = characterDao.observeAll()

    /** Equipo por slot y colección de monturas del personaje (requiere sesión). */
    suspend fun detail(character: CharacterEntity): CharacterDetail {
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        val ns = region.namespaceProfile
        val realm = character.realmSlug
        val name = character.name.lowercase()

        val equipment = runCatching {
            api.equipment(realm, name, ns).equipped_items.map {
                EquippedItem(
                    slot = it.slot?.name ?: "",
                    name = it.name ?: "",
                    itemLevel = it.level?.value ?: 0,
                    quality = it.quality?.name ?: "",
                )
            }
        }.getOrDefault(emptyList())

        val mounts = runCatching {
            api.mountsNamed(realm, name, ns).mounts.mapNotNull { it.mount.name }
        }.getOrDefault(emptyList())

        return CharacterDetail(equipment, mounts.size, mounts.sorted())
    }
}
