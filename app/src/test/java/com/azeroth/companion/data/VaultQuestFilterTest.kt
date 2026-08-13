package com.azeroth.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Qué debe salir en la lista de misiones de la bóveda, y qué no.
 *
 * El fallo que esto fija: la lista mezclaba lo que puedes hacer AHORA con lo
 * que hiciste hace meses. La API de Blizzard devuelve las misiones completadas
 * alguna vez, y el código las trataba a todas igual, así que una misión de una
 * sola vez terminada en su día salía marcada para siempre ocupando sitio, aunque
 * no vaya a volver a estar disponible jamás.
 *
 * La distinción correcta depende del periodo de reinicio de la tarea:
 *
 *  - **Semanal**: "completada" equivale a "hecha esta semana". Al pasar el reset
 *    Blizzard la saca de la lista y vuelve a estar disponible. Se muestra, y su
 *    marca es información útil.
 *  - **Una sola vez**: "completada" equivale a "hecha para siempre". No vuelve,
 *    así que no es una tarea pendiente y no debe ocupar la lista.
 *
 * Estas pruebas trabajan sobre el modelo, que es donde vive la regla, y así no
 * dependen de Android ni de la red.
 */
class VaultQuestFilterTest {

    private fun quest(id: Int, done: Boolean) =
        VaultQuest(questId = id, name = "Misión $id", zone = "Zona", done = done)

    @Test
    fun `lo pendiente es lo que se cuenta, no lo hecho`() {
        val group = VaultQuestGroup(
            taskId = "asaltos",
            title = "Asaltos",
            feedsVault = true,
            quests = listOf(quest(1, true), quest(2, false), quest(3, false)),
        )
        assertEquals(1, group.doneCount)
        assertEquals("lo que el jugador viene a preguntar", 2, group.pendingCount)
    }

    @Test
    fun `el resumen suma solo lo que aporta a la boveda`() {
        val snapshot = VaultQuestsSnapshot(
            groups = listOf(
                VaultQuestGroup("a", "Aporta", feedsVault = true,
                    quests = listOf(quest(1, true), quest(2, false))),
                VaultQuestGroup("b", "No aporta", feedsVault = false,
                    quests = listOf(quest(3, false), quest(4, false))),
            ),
            hasCharacter = true,
        )
        assertEquals(1, snapshot.vaultDone)
        assertEquals(2, snapshot.vaultTotal)
        // Las dos del grupo que no aporta no deben contarse como pendientes de
        // bóveda: hacerlas no acerca ni un poco a llenarla.
        assertEquals(1, snapshot.vaultPending)
    }

    @Test
    fun `datos de antes del reset se marcan como caducados`() {
        val reset = Instant.parse("2026-08-12T15:00:00Z")
        val antes = VaultQuestsSnapshot(
            hasCharacter = true,
            syncedAt = reset.minusSeconds(3600),
            lastReset = reset,
        )
        val despues = VaultQuestsSnapshot(
            hasCharacter = true,
            syncedAt = reset.plusSeconds(3600),
            lastReset = reset,
        )
        assertTrue(
            "una lectura anterior al reset enseña marcas de la semana pasada",
            antes.staleForThisWeek,
        )
        assertFalse(despues.staleForThisWeek)
    }

    @Test
    fun `sin fecha de lectura o sin reset no se afirma que este caducado`() {
        // Preferimos no decir nada a decir algo falso: sin una de las dos
        // fechas no hay forma de saberlo.
        assertFalse(VaultQuestsSnapshot(hasCharacter = true).staleForThisWeek)
        assertFalse(
            VaultQuestsSnapshot(hasCharacter = true, syncedAt = Instant.now()).staleForThisWeek,
        )
    }
}
