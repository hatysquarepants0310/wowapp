package com.azeroth.companion.data

import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.network.BlizzardApiFactory
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Traduce el reino del personaje activo a su ID de reino CONECTADO, que es la
 * clave con la que Blizzard publica la casa de subastas de equipo. Un reino
 * suelto no tiene subastas propias: comparte mercado con los reinos a los que
 * está conectado.
 *
 * El resultado se cachea en memoria porque no cambia jamás durante una sesión.
 */
@Singleton
class ConnectedRealmResolver @Inject constructor(
    private val activeCharacter: ActiveCharacter,
    private val settingsRepository: SettingsRepository,
    private val apiFactory: BlizzardApiFactory,
) : CharacterRepositoryPort {

    private val cache = mutableMapOf<String, Int>()

    override suspend fun activeConnectedRealmId(): Int? {
        val slug = activeCharacter.current()?.realmSlug ?: return null
        cache[slug]?.let { return it }
        val region = settingsRepository.settings.first().region
        val api = apiFactory.forRegion(region)
        val realm = runCatching { api.realm(slug, region.namespaceDynamic) }.getOrNull() ?: return null
        val id = realm.connected_realm?.connectedRealmId ?: return null
        cache[slug] = id
        return id
    }
}
