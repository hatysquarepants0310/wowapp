package com.azeroth.companion.core.catalog

import com.azeroth.companion.core.model.DetectionRule
import com.azeroth.companion.core.model.TaskCategory
import com.azeroth.companion.data.StorylinesFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Los assets que se publican en el APK tienen que parsear con los modelos de la
 * app. Un typo en el catálogo o en el pipeline de historias no debe descubrirse
 * en el móvil del usuario.
 */
class CatalogAssetTest {

    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
    private val assets = File("src/main/assets/catalog")

    @Test
    fun `el catálogo publicado parsea`() {
        val catalog = json.decodeFromString(
            Catalog.serializer(), File(assets, "catalog.json").readText(),
        )
        assertTrue(catalog.weeklyTasks.isNotEmpty())
        assertTrue(catalog.resets.isNotEmpty())
    }

    /**
     * Dos regresiones reales viven en esta prueba:
     *
     * 1. Las semanales eran ManualOnly y, sin entrada manual, se quedaban en 0/N
     *    para siempre.
     * 2. Después pasaron a usar los IDs de las misiones marcador "Midnight: X",
     *    que Blizzard NO expone en /quests/completed — comprobado sobre 75
     *    personajes activos: 15 de 16 no aparecían nunca.
     *
     * Por eso toda semanal debe detectarse con una señal que la API demuestre.
     */
    @Test
    fun `las semanales se detectan con señales comprobables`() {
        val catalog = json.decodeFromString(
            Catalog.serializer(), File(assets, "catalog.json").readText(),
        )
        val weeklies = catalog.weeklyTasks.filter { it.category != TaskCategory.GREAT_VAULT }
        assertTrue("sin semanales en el catálogo", weeklies.isNotEmpty())

        val manual = weeklies.filter { it.detectionRule == DetectionRule.ManualOnly }
        assertEquals(emptyList<String>(), manual.map { it.id })

        weeklies.forEach { task ->
            assertTrue(
                "la semanal ${task.id} no se apoya en ninguna señal medible",
                canFire(task.detectionRule),
            )
        }
    }

    /**
     * Guardia contra la regresión concreta: los IDs de las misiones marcador
     * "Midnight: X" no aparecen en /quests/completed de NADIE (comprobado sobre
     * 75 personajes activos) y en DB2 comparten un mismo UniqueBitFlag, señal de
     * que no se almacenan individualmente. Ninguna regla puede depender de ellos.
     */
    @Test
    fun `ninguna semanal depende de las misiones marcador`() {
        val catalog = json.decodeFromString(
            Catalog.serializer(), File(assets, "catalog.json").readText(),
        )
        val dead = setOf(
            93766, 93767, 93769, 93889, 93890, 93892, 93909, 93910,
            93911, 93912, 93913, 94457, 95842, 95843, 96727,
        )
        catalog.weeklyTasks.forEach { task ->
            val used = questIds(task.detectionRule).filter { it in dead }
            assertEquals("la semanal ${task.id} usa IDs marcador", emptyList<Int>(), used)
        }
    }

    private fun canFire(rule: DetectionRule): Boolean = when (rule) {
        is DetectionRule.QuestCompleted -> rule.questIds.isNotEmpty()
        is DetectionRule.QuestDelta -> rule.questIds.isNotEmpty()
        is DetectionRule.ActivityThisWeek,
        is DetectionRule.StatisticDelta,
        is DetectionRule.ReputationGain,
        is DetectionRule.MythicPlusRuns,
        is DetectionRule.RaidBossKills,
        is DetectionRule.AchievementCriteria,
        is DetectionRule.CurrencyThreshold,
        -> true
        is DetectionRule.AnyOf -> rule.rules.any { canFire(it) }
        is DetectionRule.AllOf -> rule.rules.isNotEmpty() && rule.rules.all { canFire(it) }
        DetectionRule.ManualOnly -> false
    }

    private fun questIds(rule: DetectionRule): List<Int> = when (rule) {
        is DetectionRule.QuestCompleted -> rule.questIds
        is DetectionRule.QuestDelta -> rule.questIds
        is DetectionRule.AnyOf -> rule.rules.flatMap { questIds(it) }
        is DetectionRule.AllOf -> rule.rules.flatMap { questIds(it) }
        else -> emptyList()
    }

    @Test
    fun `las historias publicadas parsean y traen campañas`() {
        val file = json.decodeFromString(
            StorylinesFile.serializer(), File(assets, "storylines.json").readText(),
        )
        assertTrue(file.storylines.size > 1000)
        assertTrue(file.campaigns.size > 50)
        // La campaña de la expansión actual debe existir y tener varios capítulos.
        val current = file.campaigns.filter { it.exp == file.currentExpansion }
        assertTrue("sin campañas en la expansión actual", current.isNotEmpty())
        assertTrue(current.maxOf { it.lines.size } >= 10)
        // Toda historia con misiones obligatorias: si no, nunca podría completarse.
        assertTrue(file.storylines.all { it.questIds.isNotEmpty() })
        assertTrue(file.storylines.any { it.opt.isNotEmpty() })
    }
}
