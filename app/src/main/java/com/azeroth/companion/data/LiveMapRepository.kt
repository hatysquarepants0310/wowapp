package com.azeroth.companion.data

import android.content.Context
import com.azeroth.companion.core.catalog.CatalogRepository
import com.azeroth.companion.core.datastore.LanguagePref
import com.azeroth.companion.core.datastore.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import com.azeroth.companion.core.network.BlizzardApiFactory
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Un punto colocable en el mapa de una zona. */
data class MapPin(
    val questId: Int,
    val name: String,
    /** Coordenada del mapa de la zona, 0..100 como en el juego. */
    val x: Double,
    val y: Double,
    val uiMapId: Int,
    val kind: PinKind,
)

enum class PinKind { ACTIVE_QUEST, WORLD_QUEST }

/** Un evento del mundo con su próxima ventana. */
data class LiveEvent(
    val id: String,
    val name: String,
    val zone: String,
    val startsAt: Instant?,
    val active: Boolean,
)

/** Una zona con lo que está pasando dentro. */
data class LiveZone(
    val uiMapId: Int,
    val name: String,
    val pins: List<MapPin>,
)

data class LiveMapSnapshot(
    val zones: List<LiveZone>,
    val events: List<LiveEvent>,
    val characterName: String?,
    val error: String? = null,
)

/**
 * Lo que está pasando ahora mismo, colocado sobre el mapa.
 *
 * Aviso sobre lo que se puede y no se puede saber: Blizzard NO publica un
 * listado global de misiones de mundo activas —no existe endpoint para eso—,
 * así que un mapa con todas las misiones de mundo de la región es imposible
 * sin estar dentro del juego. Lo que sí es real y en vivo:
 *
 *  - Las misiones que el personaje tiene ACEPTADAS ahora mismo
 *    (`/quests` → `in_progress`), incluidas las de mundo que haya cogido.
 *  - La posición exacta de cada una, de las tablas QuestPOI del cliente
 *    (`quest_coords.json`), que es la misma fuente que usa TomTom.
 *  - Los eventos con cadencia conocida (asaltos del Vacío, Tormenta del Vacío…)
 *    con su cuenta atrás real, calculada por [EventsRepository].
 *
 * Preferimos un mapa con menos cosas pero todas ciertas antes que uno lleno de
 * datos inventados.
 */
@Singleton
class LiveMapRepository @Inject constructor(
    private val activeCharacter: ActiveCharacter,
    private val settingsRepository: SettingsRepository,
    private val apiFactory: BlizzardApiFactory,
    private val storylinesRepository: StorylinesRepository,
    private val eventsRepository: EventsRepository,
    private val catalogRepository: CatalogRepository,
    @ApplicationContext private val context: Context,
) {

    private fun spanish(): Boolean =
        (LanguagePref.read(context) ?: java.util.Locale.getDefault().language).startsWith("es")

    suspend fun snapshot(): LiveMapSnapshot {
        val events = runCatching { liveEvents() }.getOrDefault(emptyList())
        val character = activeCharacter.current()
            ?: return LiveMapSnapshot(emptyList(), events, null)

        val settings = settingsRepository.settings.first()
        val api = apiFactory.forRegion(settings.region)
        val active = runCatching {
            api.activeQuests(
                character.realmSlug,
                character.name.lowercase(),
                settings.region.namespaceProfile,
            ).in_progress
        }.getOrElse {
            return LiveMapSnapshot(emptyList(), events, character.name, it.message)
        }

        val worldQuestAreas = catalogRepository.load().worldQuestAreaIds.toSet()
        val pins = active.mapNotNull { quest ->
            val coords = storylinesRepository.questCoordinates(quest.id) ?: return@mapNotNull null
            MapPin(
                questId = quest.id,
                name = quest.name.orEmpty().ifBlank { "#${quest.id}" },
                uiMapId = coords.first,
                x = coords.second,
                y = coords.third,
                kind = if (storylinesRepository.questAreaId(quest.id) in worldQuestAreas) {
                    PinKind.WORLD_QUEST
                } else {
                    PinKind.ACTIVE_QUEST
                },
            )
        }

        val zones = pins.groupBy { it.uiMapId }
            .map { (mapId, zonePins) ->
                LiveZone(
                    uiMapId = mapId,
                    name = storylinesRepository.mapName(mapId) ?: "Mapa $mapId",
                    pins = zonePins.sortedBy { it.name },
                )
            }
            .sortedByDescending { it.pins.size }

        return LiveMapSnapshot(zones, events, character.name)
    }

    private suspend fun liveEvents(): List<LiveEvent> {
        val now = Instant.now()
        val catalog = catalogRepository.load()
        val definitions = catalog.worldEvents.associateBy { it.id }
        val spanish = spanish()
        // Se mira una ventana que empieza un poco antes de ahora para no perder
        // el evento que está EN CURSO: solo mirar hacia delante lo escondería
        // justo cuando más importa.
        return eventsRepository.upcoming(now.minusSeconds(3 * 3600), hours = 24)
            .mapNotNull { occurrence ->
                val definition = definitions[occurrence.definitionId] ?: return@mapNotNull null
                LiveEvent(
                    id = definition.id,
                    name = definition.localizedName(spanish),
                    zone = definition.zone,
                    startsAt = occurrence.startsAt,
                    active = occurrence.startsAt <= now && now < occurrence.endsAt,
                )
            }
            .sortedWith(compareByDescending<LiveEvent> { it.active }.thenBy { it.startsAt })
            .take(12)
    }

    private fun com.azeroth.companion.core.model.WorldEventDefinition.localizedName(
        spanish: Boolean,
    ): String = (if (spanish) name["es_MX"] else name["en_US"])
        ?: name["en_US"] ?: name.values.firstOrNull() ?: id
}
