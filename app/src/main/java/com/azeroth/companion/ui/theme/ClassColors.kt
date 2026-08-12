package com.azeroth.companion.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colores de clase de World of Warcraft, los canónicos.
 *
 * Son vocabulario, no decoración: un jugador identifica una clase por su color
 * antes de leer su nombre, y llevan veinte años sin cambiar. No se
 * reinterpretan ni se "ajustan a la paleta"; si algo choca con ellos, lo que
 * cambia es la paleta.
 *
 * En esta app además son el acento: la interfaz se tiñe del color de TU clase.
 * Un druida ve la app en naranja y un brujo en morado, sin tocar ningún ajuste.
 */
object ClassColors {

    val DeathKnight = Color(0xFFC41E3A)
    val DemonHunter = Color(0xFFA330C9)
    val Druid = Color(0xFFFF7C0A)
    val Evoker = Color(0xFF33937F)
    val Hunter = Color(0xFFAAD372)
    val Mage = Color(0xFF3FC7EB)
    val Monk = Color(0xFF00FF98)
    val Paladin = Color(0xFFF48CBA)
    val Priest = Color(0xFFFFFFFF)
    val Rogue = Color(0xFFFFF468)
    val Shaman = Color(0xFF0070DD)
    val Warlock = Color(0xFF8788EE)
    val Warrior = Color(0xFFC69B6D)

    /** Cuando todavía no se sabe la clase: un azul neutro, no un gris muerto. */
    val Unknown = Color(0xFF7FA8D9)

    /**
     * La API devuelve el nombre de clase ya traducido al idioma del personaje,
     * así que se reconocen los dos idiomas que la app soporta. Sin acentos y en
     * minúsculas para que "Cazador de demonios" y "cazador de demonios" caigan
     * en el mismo sitio.
     */
    private val byName: Map<String, Color> = mapOf(
        // en_US
        "death knight" to DeathKnight,
        "demon hunter" to DemonHunter,
        "druid" to Druid,
        "evoker" to Evoker,
        "hunter" to Hunter,
        "mage" to Mage,
        "monk" to Monk,
        "paladin" to Paladin,
        "priest" to Priest,
        "rogue" to Rogue,
        "shaman" to Shaman,
        "warlock" to Warlock,
        "warrior" to Warrior,
        // es_MX / es_ES
        "caballero de la muerte" to DeathKnight,
        "cazador de demonios" to DemonHunter,
        "druida" to Druid,
        "evocador" to Evoker,
        "cazador" to Hunter,
        "mago" to Mage,
        "monje" to Monk,
        "paladin" to Paladin,
        "sacerdote" to Priest,
        "picaro" to Rogue,
        "chaman" to Shaman,
        "brujo" to Warlock,
        "guerrero" to Warrior,
    )

    fun forClassName(name: String?): Color {
        val key = name?.lowercase()?.let(::stripAccents)?.trim() ?: return Unknown
        return byName[key] ?: Unknown
    }

    private fun stripAccents(value: String): String = buildString {
        value.forEach { c ->
            append(
                when (c) {
                    'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; 'ü' -> 'u'
                    else -> c
                },
            )
        }
    }
}

/**
 * Colores de calidad de objeto. Igual de sagrados que los de clase: el jugador
 * los lee más rápido que el texto.
 */
object QualityColors {
    val Poor = Color(0xFF9D9D9D)
    val Common = Color(0xFFFFFFFF)
    val Uncommon = Color(0xFF1EFF00)
    val Rare = Color(0xFF0070DD)
    val Epic = Color(0xFFA335EE)
    val Legendary = Color(0xFFFF8000)
    val Artifact = Color(0xFFE6CC80)
    val Heirloom = Color(0xFF00CCFF)
}
