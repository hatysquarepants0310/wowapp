package com.azeroth.companion.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.azeroth.companion.core.model.Region
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val region: Region,
    val showLegacyContent: Boolean,
    val prewarnLongMinutes: Int,
    val prewarnShortMinutes: Int,
    val resetWarnHours: Int,
    val quietHoursStart: Int?,
    val quietHoursEnd: Int?,
    val quietHoursExceptEventPrewarn: Boolean,
    val activeCharacterId: Long?,
    val viabilityFilterEnabled: Boolean,
    /** "es", "en" o null = seguir el idioma del sistema (autodetección). */
    val language: String?,
    /**
     * Descargar el arte de los mapas del juego para pintarlos de verdad.
     * Son unos 500 kB por zona la primera vez y luego queda en caché; se puede
     * apagar para no gastar datos, y entonces el mapa se dibuja sin fondo.
     */
    val downloadMapArt: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val REGION = stringPreferencesKey("region")
        val SHOW_LEGACY = booleanPreferencesKey("show_legacy")
        val PREWARN_LONG = intPreferencesKey("prewarn_long_min")
        val PREWARN_SHORT = intPreferencesKey("prewarn_short_min")
        val RESET_WARN_H = intPreferencesKey("reset_warn_hours")
        val QUIET_START = intPreferencesKey("quiet_start_hour")
        val QUIET_END = intPreferencesKey("quiet_end_hour")
        val QUIET_EXCEPT_PREWARN = booleanPreferencesKey("quiet_except_prewarn")
        val ACTIVE_CHARACTER = longPreferencesKey("active_character_id")
        val VIABILITY_FILTER = booleanPreferencesKey("viability_filter")
        val LANGUAGE = stringPreferencesKey("language")
        val MAP_ART = booleanPreferencesKey("download_map_art")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            region = p[Keys.REGION]?.let { runCatching { Region.valueOf(it) }.getOrNull() } ?: Region.US,
            showLegacyContent = p[Keys.SHOW_LEGACY] ?: false,
            prewarnLongMinutes = (p[Keys.PREWARN_LONG] ?: 15).coerceIn(5, 60),
            prewarnShortMinutes = (p[Keys.PREWARN_SHORT] ?: 3).coerceIn(1, 10),
            resetWarnHours = (p[Keys.RESET_WARN_H] ?: 12).coerceIn(1, 48),
            quietHoursStart = p[Keys.QUIET_START],
            quietHoursEnd = p[Keys.QUIET_END],
            quietHoursExceptEventPrewarn = p[Keys.QUIET_EXCEPT_PREWARN] ?: true,
            activeCharacterId = p[Keys.ACTIVE_CHARACTER],
            viabilityFilterEnabled = p[Keys.VIABILITY_FILTER] ?: false,
            language = p[Keys.LANGUAGE],
            downloadMapArt = p[Keys.MAP_ART] ?: true,
        )
    }

    /** Idioma elegido, o null si se sigue el del sistema. Lectura síncrona para el arranque. */
    suspend fun language(): String? = context.dataStore.data.first()[Keys.LANGUAGE]

    suspend fun setDownloadMapArt(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MAP_ART] = enabled }
    }

    suspend fun setLanguage(tag: String?) = context.dataStore.edit {
        if (tag == null) it.remove(Keys.LANGUAGE) else it[Keys.LANGUAGE] = tag
    }

    suspend fun setRegion(region: Region) =
        context.dataStore.edit { it[Keys.REGION] = region.name }

    suspend fun setShowLegacy(show: Boolean) =
        context.dataStore.edit { it[Keys.SHOW_LEGACY] = show }

    suspend fun setPrewarnMinutes(long: Int, short: Int) = context.dataStore.edit {
        it[Keys.PREWARN_LONG] = long
        it[Keys.PREWARN_SHORT] = short
    }

    suspend fun setActiveCharacter(id: Long) =
        context.dataStore.edit { it[Keys.ACTIVE_CHARACTER] = id }

    suspend fun setViabilityFilter(enabled: Boolean) =
        context.dataStore.edit { it[Keys.VIABILITY_FILTER] = enabled }
}
