package com.azeroth.companion.core.catalog

import com.azeroth.companion.core.model.DetectionRule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reglas que los IDs de misión de las semanales tienen que cumplir. Cada una
 * viene de un fallo real visto en la app, no de un principio abstracto.
 */
class WeeklyQuestIdsTest {

    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
    private val catalog: Catalog = json.decodeFromString(
        Catalog.serializer(),
        File("src/main/assets/catalog/catalog.json").readText(),
    )

    private fun questIds(rule: DetectionRule): List<Int> = when (rule) {
        is DetectionRule.QuestCompleted -> rule.questIds
        is DetectionRule.QuestDelta -> rule.questIds
        is DetectionRule.AnyOf -> rule.rules.flatMap { questIds(it) }
        is DetectionRule.AllOf -> rule.rules.flatMap { questIds(it) }
        else -> emptyList()
    }

    /**
     * Regresión: la misión 96442 ("Búsqueda de conocimiento semana 3 de 5:
     * Asaltos a líneas ley") estaba a la vez en `weekly_seeking_knowledge` y en
     * `weekly_void_assaults`. Resultado: al hacer la del Omnium se marcaban
     * también los asaltos del Vacío, que el jugador no había tocado.
     */
    @Test
    fun `ninguna misión pertenece a dos semanales`() {
        val owner = mutableMapOf<Int, String>()
        val clashes = mutableListOf<String>()
        catalog.weeklyTasks.forEach { task ->
            questIds(task.detectionRule).forEach { id ->
                val previous = owner[id]
                if (previous != null && previous != task.id) {
                    clashes += "$id en $previous y ${task.id}"
                }
                owner[id] = task.id
            }
        }
        assertEquals("misiones compartidas entre semanales: $clashes", 0, clashes.size)
    }

    /**
     * Las series rotatorias ("semana N de 5") son misiones DISTINTAS que quedan
     * completadas para siempre. Marcarlas por presencia absoluta dejaba la
     * tarea hecha el resto de la temporada, así que tienen que ir declaradas
     * como no repetibles para que el motor exija una lectura previa al reset.
     */
    @Test
    fun `las series rotatorias no se declaran repetibles`() {
        val rotating = listOf("weekly_seeking_knowledge")
        rotating.forEach { id ->
            val task = catalog.weeklyTasks.first { it.id == id }
            val rules = flatten(task.detectionRule).filterIsInstance<DetectionRule.QuestCompleted>()
            assertTrue("$id no tiene regla de misión", rules.isNotEmpty())
            assertTrue(
                "$id debe declararse no repetible",
                rules.any { !it.repeatable },
            )
        }
    }

    /** Un ID de misión de Midnight nunca es 0 ni negativo. */
    @Test
    fun `todos los ids son válidos`() {
        catalog.weeklyTasks.forEach { task ->
            questIds(task.detectionRule).forEach { id ->
                assertTrue("${task.id} tiene un ID inválido: $id", id > 0)
            }
        }
    }

    private fun flatten(rule: DetectionRule): List<DetectionRule> = when (rule) {
        is DetectionRule.AnyOf -> rule.rules.flatMap { flatten(it) }
        is DetectionRule.AllOf -> rule.rules.flatMap { flatten(it) }
        else -> listOf(rule)
    }
}
