package com.azeroth.companion.core.catalog

import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regresión: `vault.worldContributingTaskIds` traía IDs que no correspondían a
 * ninguna tarea real ("delves_bountiful", "prey_eversong"…), y encima el
 * catálogo publicado no incluía bloque `vault`, así que se usaban esos valores
 * por defecto y nadie se enteraba: la fila de Mundo simplemente nunca recibía
 * nada de ahí.
 */
class VaultContributorsTest {

    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
    private val catalog: Catalog = json.decodeFromString(
        Catalog.serializer(),
        File("src/main/assets/catalog/catalog.json").readText(),
    )

    @Test
    fun `los aportes a la bóveda apuntan a tareas que existen`() {
        val known = catalog.weeklyTasks.map { it.id }.toSet()
        val unknown = catalog.vault.worldContributingTaskIds.filterNot { it in known }
        assertTrue("IDs que no existen en weeklyTasks: $unknown", unknown.isEmpty())
    }

    @Test
    fun `el catálogo publicado declara sus aportes`() {
        assertTrue(
            "sin esta lista la sección de misiones de bóveda queda vacía",
            catalog.vault.worldContributingTaskIds.isNotEmpty(),
        )
    }

    /**
     * La misma comprobación para el valor por defecto de Kotlin: es el que se
     * usa si un catálogo descargado no trae el bloque, que es exactamente como
     * pasó inadvertido el fallo anterior.
     */
    @Test
    fun `el valor por defecto también apunta a tareas reales`() {
        val known = catalog.weeklyTasks.map { it.id }.toSet()
        val unknown = VaultRules().worldContributingTaskIds.filterNot { it in known }
        assertTrue("IDs por defecto inexistentes: $unknown", unknown.isEmpty())
    }
}
