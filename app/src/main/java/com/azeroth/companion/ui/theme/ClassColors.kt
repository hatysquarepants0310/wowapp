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

    /** Las trece, para poder recorrerlas en las pruebas de contraste. */
    val all: List<Pair<String, Color>> = listOf(
        "DeathKnight" to DeathKnight, "DemonHunter" to DemonHunter, "Druid" to Druid,
        "Evoker" to Evoker, "Hunter" to Hunter, "Mage" to Mage, "Monk" to Monk,
        "Paladin" to Paladin, "Priest" to Priest, "Rogue" to Rogue, "Shaman" to Shaman,
        "Warlock" to Warlock, "Warrior" to Warrior, "Unknown" to Unknown,
    )

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

    /** Los ocho, para recorrerlos en las pruebas de contraste. */
    val all: List<Pair<String, Color>> = listOf(
        "Poor" to Poor, "Common" to Common, "Uncommon" to Uncommon, "Rare" to Rare,
        "Epic" to Epic, "Legendary" to Legendary, "Artifact" to Artifact,
        "Heirloom" to Heirloom,
    )
}

/**
 * # Colores oficiales y legibilidad: cómo conviven
 *
 * La prueba de contraste midió lo que se veía en las capturas: el rojo de
 * Caballero de la Muerte (#C41E3A) queda en **2,69:1** sobre el panel elevado, y
 * el azul de Chamán en 3,26. Como texto pequeño eso no se lee.
 *
 * La regla no negociable dice que los colores de clase y de calidad **no se
 * aproximan nunca**. Y es correcta: el jugador lee "morado = épico" más rápido
 * que la palabra "épico", y falsear el tono rompe justo la información que hace
 * útil el color. Así que no se tocan.
 *
 * La salida no es cambiar el color, es distinguir **qué papel cumple**:
 *
 *  - **Marca de identidad** —el marco del tooltip, el punto de clase, el borde
 *    del icono, el relleno de una barra, el icono de la pestaña activa—: color
 *    oficial EXACTO, siempre, sin excepción. Son elementos gráficos, y el
 *    umbral que les aplica es 3:1.
 *  - **Texto pequeño teñido** —la etiqueta de una pestaña, el rótulo de un
 *    botón—: ahí no hay identidad que comunicar, solo se está usando el acento
 *    como color de énfasis. Para eso se usa [readableOn], que aclara lo justo
 *    hasta llegar a 4,5:1.
 *
 * Nunca se aclara un color que esté haciendo de marca de identidad, y siempre
 * hay una marca de identidad con el tono exacto al lado del texto aclarado, así
 * que el tono real sigue estando presente en pantalla.
 *
 * El nombre del objeto dentro del tooltip es la excepción deliberada: ahí va el
 * color exacto aunque Raro se quede en 4,21 y Épico en 4,15. Es lo que hace el
 * juego, píxel por píxel, sobre el mismo fondo casi negro, y ese elemento existe
 * precisamente para ser idéntico al del juego. Además lleva el marco del mismo
 * color rodeándolo, que es la marca de identidad.
 */
private fun canal(c: Float): Double {
    val s = c.toDouble()
    return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
}

private fun luminancia(color: Color): Double =
    0.2126 * canal(color.red) + 0.7152 * canal(color.green) + 0.0722 * canal(color.blue)

/** Contraste WCAG 2.1 entre dos colores opacos. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = luminancia(a)
    val lb = luminancia(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/**
 * Devuelve el color aclarado lo justo para alcanzar [target] sobre [background].
 * Si ya lo cumple, lo devuelve intacto — que es el caso de la mayoría de clases.
 *
 * Se mezcla hacia el blanco en pasos pequeños en vez de saltar a un tono
 * inventado: así el matiz se conserva y un rojo sigue siendo rojo.
 */
fun Color.readableOn(background: Color, target: Double = 4.5): Color {
    if (contrastRatio(this, background) >= target) return this
    var mix = 0f
    var out = this
    while (mix < 1f && contrastRatio(out, background) < target) {
        mix += 0.02f
        out = Color(
            red = red + (1f - red) * mix,
            green = green + (1f - green) * mix,
            blue = blue + (1f - blue) * mix,
            alpha = alpha,
        )
    }
    return out
}
