package com.azeroth.companion.data

import android.content.Context
import com.azeroth.companion.core.catalog.CatalogRepository
import com.azeroth.companion.core.database.SnapshotDao
import com.azeroth.companion.core.datastore.LanguagePref
import com.azeroth.companion.core.model.DetectionRule
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
}

data class VaultQuestsSnapshot(
    val groups: List<VaultQuestGroup> = emptyList(),
    val hasCharacter: Boolean = false,
    val syncedAt: Instant? = null,
) {
    /** Actividades hechas que aportan a la bóveda. */
    val vaultDone: Int get() = groups.filter { it.feedsVault }.sumOf { it.doneCount }
    val vaultTotal: Int get() = groups.filter { it.feedsVault }.sumOf { it.quests.size }
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

        val groups = catalog.weeklyTasks.mapNotNull { task ->
            val ids = questIdsOf(task.detectionRule)
            if (ids.isEmpty() || ids.size > MAX_QUESTS_PER_GROUP) return@mapNotNull null
            val quests = ids.mapNotNull { id ->
                // Sin nombre no hay fila: un "#93416" no le dice nada a nadie.
                val name = storylinesRepository.questName(id) ?: return@mapNotNull null
                VaultQuest(
                    questId = id,
                    name = name,
                    zone = storylinesRepository.questZoneName(id),
                    done = id in completed,
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
        }.sortedWith(compareByDescending<VaultQuestGroup> { it.feedsVault }.thenBy { it.title })

        return VaultQuestsSnapshot(
            groups = groups,
            hasCharacter = true,
            syncedAt = snapshot.takenAt,
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
