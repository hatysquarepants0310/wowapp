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
     * La regresión que motivó esta prueba: casi todas las semanales eran
     * ManualOnly y, como la app no tiene entrada manual, se quedaban en 0/N para
     * siempre. Toda semanal que no sea de la Gran Bóveda debe traer detección
     * automática con IDs de misión reales.
     */
    @Test
    fun `las semanales se detectan solas`() {
        val catalog = json.decodeFromString(
            Catalog.serializer(), File(assets, "catalog.json").readText(),
        )
        val manual = catalog.weeklyTasks
            .filter { it.category != TaskCategory.GREAT_VAULT }
            .filter { it.detectionRule == DetectionRule.ManualOnly }
        assertEquals(emptyList<String>(), manual.map { it.id })

        catalog.weeklyTasks
            .mapNotNull { it.detectionRule as? DetectionRule.QuestCompleted }
            .forEach { rule ->
                assertTrue("regla sin IDs de misión", rule.questIds.isNotEmpty())
                assertTrue("ID de misión inverosímil", rule.questIds.all { it > 0 })
            }
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
