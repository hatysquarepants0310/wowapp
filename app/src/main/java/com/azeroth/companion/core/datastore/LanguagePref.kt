package com.azeroth.companion.core.datastore

import android.content.Context

/**
 * Lectura ligera y síncrona del idioma elegido, para aplicarlo en
 * attachBaseContext antes de que exista inyección de dependencias.
 * Se guarda además en SharedPreferences para no depender de DataStore ahí.
 */
object LanguagePref {
    private const val FILE = "language_pref"
    private const val KEY = "language"

    fun read(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)

    fun write(context: Context, tag: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            if (tag == null) remove(KEY) else putString(KEY, tag)
        }.apply()
    }
}
