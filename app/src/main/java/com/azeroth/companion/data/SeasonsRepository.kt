package com.azeroth.companion.data

import com.azeroth.companion.core.database.CharacterDao
import com.azeroth.companion.core.datastore.SettingsRepository
import com.azeroth.companion.core.network.BlizzardApiFactory
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class SeasonProgress(
    val seasonId: Int,
    val isCurrent: Boolean,
    val rating: Int,
    val runCount: Int,
    val bestLevel: Int,
    val participated: Boolean,
)

@Singleton
class SeasonsRepository @Inject constructor(
    private val apiFactory: BlizzardApiFactory,
    private val settingsRepository: SettingsRepository,
    private val characterDao: CharacterDao,
) {
    /**
     * Progreso de Mythic+ por temporada del personaje activo (§2.3). Sin sesión,
     * devuelve solo la lista de temporadas sin datos personales.
     */
    suspend fun seasons(): List<SeasonProgress> {
        val settings = settingsRepository.settings.first()
        val region = settings.region
        val api = apiFactory.forRegion(region)

        val index = runCatching { api.mythicSeasonIndex(region.namespaceDynamic) }.getOrNull()
            ?: return emptyList()
        val currentId = index.current_season?.id ?: index.seasons.maxOfOrNull { it.id } ?: 0
        val character = characterDao.observeAll().first()
            .firstOrNull { it.id == settings.activeCharacterId }
            ?: characterDao.observeAll().first().firstOrNull()

        return index.seasons.sortedByDescending { it.id }.map { season ->
            val detail = if (character != null) {
                runCatching {
                    api.mythicSeason(
                        character.realmSlug, character.name.lowercase(), season.id,
                        region.namespaceProfile,
                    )
                }.getOrNull()
            } else null
            val runs = detail?.best_runs.orEmpty()
            SeasonProgress(
                seasonId = season.id,
                isCurrent = season.id == currentId,
                rating = detail?.mythic_rating?.rating?.toInt() ?: 0,
                runCount = runs.size,
                bestLevel = runs.maxOfOrNull { it.keystone_level } ?: 0,
                participated = runs.isNotEmpty(),
            )
        }
    }
}
