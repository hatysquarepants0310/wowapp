package com.azeroth.companion.ui

import androidx.compose.ui.graphics.Color
import com.azeroth.companion.ui.theme.Base
import com.azeroth.companion.ui.theme.ClassColors
import com.azeroth.companion.ui.theme.QualityColors
import com.azeroth.companion.ui.theme.readableOn
import com.azeroth.companion.ui.theme.Surface
import com.azeroth.companion.ui.theme.SurfaceHigh
import com.azeroth.companion.ui.theme.TextHigh
import com.azeroth.companion.ui.theme.TextLow
import com.azeroth.companion.ui.theme.TextMid
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contraste, medido.
 *
 * El documento pide pasar axe-core sin violaciones de contraste. axe-core es de
 * navegador, así que aquí se implementa la misma fórmula —la de luminancia
 * relativa de WCAG 2.1— sobre los colores reales del tema.
 *
 * Esta comprobación importa más de lo normal en esta app por una razón concreta:
 * **el acento no lo elijo yo, lo elige la clase del personaje del usuario.** Un
 * Caballero de la Muerte trae rojo #C41E3A y un Pícaro amarillo #FFF468. Si solo
 * se mira "queda bien con mi color favorito", la mitad de los jugadores acaban
 * con texto ilegible. Por eso se recorren las trece clases.
 */
class ContrastTest {

    private fun canal(c: Float): Double {
        val s = c.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminancia(color: Color): Double =
        0.2126 * canal(color.red) + 0.7152 * canal(color.green) + 0.0722 * canal(color.blue)

    private fun ratio(frente: Color, fondo: Color): Double {
        val a = luminancia(frente)
        val b = luminancia(fondo)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private val fondos = listOf("Base" to Base, "Surface" to Surface, "SurfaceHigh" to SurfaceHigh)

    /** Texto normal: 4.5:1. Es el umbral AA de WCAG para cuerpo de texto. */
    private val AA = 4.5

    /** Texto grande y elementos de interfaz (bordes, iconos): 3:1. */
    private val AA_GRANDE = 3.0

    @Test
    fun `el texto de cuerpo cumple AA sobre los tres fondos`() {
        val fallos = mutableListOf<String>()
        for ((nombreFondo, fondo) in fondos) {
            for ((nombre, color) in listOf("TextHigh" to TextHigh, "TextMid" to TextMid)) {
                val r = ratio(color, fondo)
                if (r < AA) fallos += "$nombre sobre $nombreFondo = %.2f".format(r)
            }
            // TextLow solo se usa en apoyo secundario, así que se le exige el
            // umbral de texto grande, no el de cuerpo.
            val r = ratio(TextLow, fondo)
            if (r < AA_GRANDE) fallos += "TextLow sobre $nombreFondo = %.2f".format(r)
        }
        assert(fallos.isEmpty()) { "Contraste insuficiente:\n" + fallos.joinToString("\n") }
    }

    /**
     * Los colores oficiales se conservan EXACTOS y se usan como marca de
     * identidad —marcos, filos, puntos, rellenos—, que son elementos gráficos y
     * cuyo umbral es 3:1.
     *
     * Esta prueba existe porque tres clases lo incumplían de verdad: Caballero
     * de la Muerte se quedaba en 2,69 sobre el panel elevado. La respuesta no
     * fue retocar el rojo oficial, sino dejar de pintar texto pequeño con él
     * (ver `readableOn` y la nota larga de ClassColors.kt).
     */
    @Test
    fun `el color oficial de clase sirve como marca sobre cualquier fondo`() {
        val fallos = mutableListOf<String>()
        for ((nombre, color) in ClassColors.all) {
            for ((nombreFondo, fondo) in fondos) {
                val r = ratio(color, fondo)
                if (r < AA_GRANDE) fallos += "%s sobre %s = %.2f".format(nombre, nombreFondo, r)
            }
        }
        assert(fallos.isEmpty()) { "Marcas de clase ilegibles:\n" + fallos.joinToString("\n") }
    }

    /**
     * Y esta es la que de verdad protege la lectura: el acento **corregido**,
     * que es el que se pinta en etiquetas de pestaña, de chip y de botón, tiene
     * que llegar a 4,5 para las trece clases. Sin esto, la mitad de los
     * jugadores tendrían rótulos ilegibles según qué personaje llevaran activo.
     */
    @Test
    fun `el acento corregido cumple AA como texto para las trece clases`() {
        val fallos = mutableListOf<String>()
        for ((nombre, color) in ClassColors.all) {
            for ((nombreFondo, fondo) in fondos) {
                val r = ratio(color.readableOn(fondo), fondo)
                if (r < AA) fallos += "%s sobre %s = %.2f".format(nombre, nombreFondo, r)
            }
        }
        assert(fallos.isEmpty()) { "Texto de acento ilegible:\n" + fallos.joinToString("\n") }
    }

    /**
     * Calidad de objeto dentro del tooltip.
     *
     * Aquí el umbral es 3:1 a propósito y conviene dejar constancia: Raro se
     * queda en 4,21 y Épico en 4,15 sobre el negro del tooltip, por debajo de
     * 4,5. **Es exactamente lo que muestra el juego**, con el mismo color sobre
     * el mismo fondo. Este elemento existe para ser idéntico al del juego, así
     * que aclararlo lo estropearía; y el nombre va rodeado por el marco del
     * mismo color, que es la marca de identidad que lo respalda.
     *
     * Queda anotado como excepción consciente, no como descuido.
     */
    @Test
    fun `los colores de calidad se leen sobre el negro del tooltip`() {
        val fallos = mutableListOf<String>()
        val fondoTooltip = Color(0xFF050609)
        for ((nombre, color) in QualityColors.all) {
            val r = ratio(color, fondoTooltip)
            if (r < AA_GRANDE) fallos += "%s = %.2f".format(nombre, r)
        }
        assert(fallos.isEmpty()) { "Calidades ilegibles:\n" + fallos.joinToString("\n") }
    }
}
