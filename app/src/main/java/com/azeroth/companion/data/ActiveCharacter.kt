package com.azeroth.companion.data

import com.azeroth.companion.core.database.CharacterDao
import com.azeroth.companion.core.database.CharacterEntity
import com.azeroth.companion.core.datastore.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Única fuente de verdad de "qué personaje estamos mirando": el elegido en
 * Ajustes y, si no hay ninguno elegido, el primero del roster.
 *
 * Vive aquí porque tener la regla copiada en cada repositorio ya causó un bug
 * real: la pantalla de semanales resolvía el personaje como
 * `activeCharacterId ?: 0L` mientras el sync guardaba el estado con el ID real
 * del personaje, así que las semanales hechas nunca aparecían marcadas aunque la
 * misión sí saliera completada en el resto de la app.
 */
@Singleton
class ActiveCharacter @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val characterDao: CharacterDao,
) {
    suspend fun current(): CharacterEntity? {
        val activeId = settingsRepository.settings.first().activeCharacterId
        val roster = characterDao.observeAll().first()
        return roster.firstOrNull { it.id == activeId } ?: roster.firstOrNull()
    }

    /** ID del personaje activo, o null si el roster está vacío. */
    suspend fun currentId(): Long? = current()?.id
}
