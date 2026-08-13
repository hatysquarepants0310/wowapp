package com.azeroth.companion.data

import android.content.Context
import com.azeroth.companion.core.catalog.CatalogRepository
import com.azeroth.companion.core.database.SnapshotDao
import com.azeroth.companion.core.datastore.LanguagePref
import com.azeroth.companion.core.model.DetectionRule
import com.azeroth.companion.core.model.ResetPeriod
import com.azeroth.companion.core.model.TrackedTask
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Una misión concreta que se puede hacer esta semana. */
data class VaultQuest(
    val questId: Int,
    val name: String,
    val zone: String?,
    /**
     * Hecha **en esta semana de reset**. Para una misión semanal es lo que
     * dice la API: al pasar el reset deja de estar en completadas y vuelve a
     * aparecer aquí como pendiente.
     */
    val done: Boolean,
)

/** Un grupo de misiones semanales (una Delve, una presa, un asalto…). */
data class VaultQuestGroup(
    val taskId: String,
    val title: String,
    /** Si el catálogo declara que este grupo aporta a la fila de Mundo. */
    val feedsVault: Boolean,
    val quests: List<VaultQuest>,
) {
    val doneCount: Int get() = quests.count { it.done }

    /** Lo que de verdad se viene a buscar: cuántas quedan por hacer. */
    val pendingCount: Int get() = quests.count { !it.done }
}

data class VaultQuestsSnapshot(
    val groups: List<VaultQuestGroup> = emptyList(),
    val hasCharacter: Boolean = false,
    val syncedAt: Instant? = null,
    /** Inicio de la semana de reset en curso, para saber si los datos sirven. */
    val lastReset: Instant? = null,
    /** Misiones de una sola vez que ya están hechas y por tanto se ocultaron. */
    val hiddenForever: Int = 0,
) {
    /** Actividades hechas que aportan a la bóveda. */
    val vaultDone: Int get() = groups.filter { it.feedsVault }.sumOf { it.doneCount }
    val vaultTotal: Int get() = groups.filter { it.feedsVault }.sumOf { it.quests.size }

    /** Cuántas quedan por hacer esta semana y aportan a la bóveda. */
    val vaultPending: Int get() = groups.filter { it.feedsVault }.sumOf { it.pendingCount }

    /**
     * Los datos son de ANTES del último reset, así que las marcas de hecho son
     * de la semana pasada. Sin esto la lista miente con toda la confianza del
     * mundo: enseña como hechas cosas que ya han vuelto a estar disponibles.
     */
    val staleForThisWeek: Boolean
        get() {
            val visto = syncedAt ?: return false
            val reset = lastReset ?: return false
            return visto.isBefore(reset)
        }
}

/**
 * Las misiones que se pueden hacer esta semana, una por una y con nombre.
 *
 * La pantalla anterior enseñaba diecisiete "tareas" abstractas con un contador;
 * servía para saber si algo estaba hecho, pero no para decidir qué hacer.
 * Aquí cada fila es una misión de verdad: se puede abrir su ficha, ver su botín
 * y copiar su comando de TomTom.
 *
 * Qué grupos aportan a la Gran Bóveda lo dice el catálogo
 * (`vault.worldContributingTaskIds`), no el código: es un dato de parche y la
 * comunidad tiene que poder corregirlo sin esperar a un APK nuevo.
 *
 * Se listan solo las familias manejables. "Presas" son 92 misiones que rotan a
 * diario: enseñarlas todas sería ruido, no ayuda.
 */
@Singleton
class VaultQuestsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val storylinesRepository: StorylinesRepository,
    private val snapshotDao: SnapshotDao,
    private val activeCharacter: ActiveCharacter,
    private val eventsRepository: EventsRepository,
    private val json: Json,
) {

    suspend fun load(): VaultQuestsSnapshot {
        val catalog = catalogRepository.load()
        val character = activeCharacter.current() ?: return VaultQuestsSnapshot()
        val snapshot = snapshotDao.latest(character.id)
            ?: return VaultQuestsSnapshot(hasCharacter = true)

        val completed = runCatching {
            json.decodeFromString(
                ListSerializer(Int.serializer()), snapshot.completedQuestIdsJson,
            ).toSet()
        }.getOrDefault(emptySet())

        val feeders = catalog.vault.worldContributingTaskIds.toSet()
        val spanish = spanish()
        val lastReset = runCatching {
            eventsRepository.resetClock().lastWeeklyReset(Instant.now())
        }.getOrNull()

        var hiddenForever = 0

        val groups = catalog.weeklyTasks.mapNotNull { task ->
            val ids = questIdsOf(task.detectionRule)
            if (ids.isEmpty() || ids.size > MAX_QUESTS_PER_GROUP) return@mapNotNull null

            // Qué significa "completada" depende de si la tarea se repite.
            //
            // La API devuelve las misiones que el personaje ha completado
            // ALGUNA VEZ. Para una misión semanal eso equivale a "hecha esta
            // semana", porque al pasar el reset Blizzard la quita de esa lista
            // y vuelve a estar disponible. Para una misión de UNA SOLA VEZ
            // significa "hecha para siempre".
            //
            // Antes se trataban igual, y por eso la lista mezclaba lo que
            // puedes hacer ahora con cosas que hiciste hace meses y no van a
            // volver. Eso no es una lista de tareas, es un historial.
            val repeats = task.resetPeriod != ResetPeriod.ONE_TIME

            val quests = ids.mapNotNull { id ->
                // Sin nombre no hay fila: un "#93416" no le dice nada a nadie.
                val name = storylinesRepository.questName(id) ?: return@mapNotNull null
                val hecha = id in completed
                // Una misión de una sola vez ya hecha no vuelve: fuera de la
                // lista. Se cuentan para poder decir cuántas se ocultaron, que
                // es distinto de esconderlas sin avisar.
                if (hecha && !repeats) {
                    hiddenForever++
                    return@mapNotNull null
                }
                VaultQuest(
                    questId = id,
                    name = name,
                    zone = storylinesRepository.questZoneName(id),
                    done = hecha,
                )
            }
            if (quests.isEmpty()) return@mapNotNull null
            VaultQuestGroup(
                taskId = task.id,
                title = task.localizedTitle(spanish),
                feedsVault = task.id in feeders,
                // Lo pendiente primero: es lo que el jugador viene a buscar.
                quests = quests.sortedWith(compareBy({ it.done }, { it.name })),
            )
        }
            // Los grupos sin nada pendiente se van al final: siguen siendo
            // útiles para confirmar que están hechos, pero no deben ocupar la
            // primera pantalla.
            .sortedWith(
                compareByDescending<VaultQuestGroup> { it.pendingCount > 0 }
                    .thenByDescending { it.feedsVault }
                    .thenBy { it.title },
            )

        return VaultQuestsSnapshot(
            groups = groups,
            hasCharacter = true,
            syncedAt = snapshot.takenAt,
            lastReset = lastReset,
            hiddenForever = hiddenForever,
        )
    }

    private fun TrackedTask.localizedTitle(spanish: Boolean): String =
        (if (spanish) title["es_MX"] else title["en_US"])
            ?: title["en_US"] ?: title.values.firstOrNull() ?: id

    private fun questIdsOf(rule: DetectionRule): List<Int> = when (rule) {
        is DetectionRule.QuestCompleted -> rule.questIds
        is DetectionRule.QuestDelta -> rule.questIds
        is DetectionRule.AnyOf -> rule.rules.flatMap { questIdsOf(it) }
        is DetectionRule.AllOf -> rule.rules.flatMap { questIdsOf(it) }
        else -> emptyList()
    }

    private fun spanish(): Boolean =
        (LanguagePref.read(context) ?: java.util.Locale.getDefault().language).startsWith("es")

    private companion object {
        /**
         * Por encima de esto la lista deja de ayudar. "Presas" tiene 92 misiones
         * que rotan a diario y "Viviendas" 100 búsquedas de decoración: enseñar
         * todas sería enterrar lo importante.
         */
        const val MAX_QUESTS_PER_GROUP = 20
    }
}
