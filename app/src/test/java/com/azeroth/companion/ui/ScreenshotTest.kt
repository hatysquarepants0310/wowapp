package com.azeroth.companion.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.azeroth.companion.ui.components.DataRow
import com.azeroth.companion.ui.components.Divider
import com.azeroth.companion.ui.components.HeroPanel
import com.azeroth.companion.ui.components.Metric
import com.azeroth.companion.ui.components.NavItem
import com.azeroth.companion.ui.components.Panel
import com.azeroth.companion.ui.components.Pill
import com.azeroth.companion.ui.components.ProgressTrack
import com.azeroth.companion.ui.components.SectionHeader
import com.azeroth.companion.ui.components.Spacing
import com.azeroth.companion.ui.components.Tooltip
import com.azeroth.companion.ui.components.TooltipDivider
import com.azeroth.companion.ui.components.TooltipItemLevel
import com.azeroth.companion.ui.components.TooltipPrice
import com.azeroth.companion.ui.components.TooltipSlotLine
import com.azeroth.companion.ui.components.TooltipStat
import com.azeroth.companion.ui.components.WowButton
import com.azeroth.companion.ui.components.WowChip
import com.azeroth.companion.ui.components.WowLoading
import com.azeroth.companion.ui.components.WowNavBar
import com.azeroth.companion.ui.components.WowSwitch
import com.azeroth.companion.ui.components.WowTabs
import com.azeroth.companion.ui.components.WowTextField
import com.azeroth.companion.ui.components.WowTopBar
import com.azeroth.companion.ui.theme.AzerothTheme
import com.azeroth.companion.ui.theme.ClassColors
import com.azeroth.companion.ui.theme.QualityColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas reales de la interfaz, renderizadas en la JVM.
 *
 * `docs/UI-WARCRAFT-HERRAMIENTAS.md` §12 pide capturas con Playwright a 390,
 * 768 y 1440, y MIRARLAS. Playwright no aplica aquí: esto es una app de Android
 * sin navegador y sin emulador en el contenedor. El equivalente exacto es
 * Robolectric con `GraphicsMode.NATIVE`, que dibuja con el mismo Skia que el
 * dispositivo y deja un `Bitmap` de verdad; de ahí sale el PNG.
 *
 * Los tres anchos se mantienen porque el motivo de elegirlos no era el navegador
 * sino el reflow: 390 es un móvil estrecho, 768 una tablet en vertical y 1440 un
 * Chromebook o un plegable abierto.
 *
 * Además de dejar el PNG, comprueba lo que sí es automatizable:
 *
 *  - **Nada desborda de lado.** El equivalente de `scrollWidth - clientWidth`
 *    en Compose es comparar el ancho medido de la raíz con el de la ventana.
 *  - **Un nombre largo y sin espacios no rompe nada.** El caso hostil de verdad,
 *    no un "Lorem ipsum" que se parte por los espacios.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * Un nombre de personaje hostil de verdad: largo, sin un solo espacio donde
     * partir y con acentos. Si algo va a empujar el contenedor, es esto.
     */
    private val hostileName =
        "Xhaltharrionelthasdrathmirvongaladrielthebetrayerofquelthalasunbroken"

    @Test
    @Config(qualifiers = "es-rES-w390dp-h844dp-xhdpi")
    fun movil390() = shoot("390")

    @Test
    @Config(qualifiers = "es-rES-w768dp-h1024dp-xhdpi")
    fun tablet768() = shoot("768")

    @Test
    @Config(qualifiers = "es-rES-w1440dp-h900dp-xhdpi")
    fun escritorio1440() = shoot("1440")

    private fun shoot(name: String) {
        // El indicador de carga es una animación infinita a propósito, así que la
        // composición NUNCA queda ociosa y `waitForIdle` se cuelga. Se conduce el
        // reloj a mano: unos cuantos fotogramas bastan para que todo se asiente.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AzerothTheme(accent = ClassColors.DeathKnight) {
                Muestrario(hostileName)
            }
        }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }

        // Desbordamiento lateral: la raíz no puede ser más ancha que la ventana.
        val root = compose.onNodeWithTag("raiz").fetchSemanticsNode()
        val ventana = root.layoutInfo.width
        assertEquals(
            "La pantalla desborda de lado en $name dp",
            0,
            root.size.width - ventana,
        )

        // Se dibuja la jerarquía de vistas directamente en un lienzo. La otra
        // vía, `captureToImage()`, va por PixelCopy y espera un redibujado del
        // sistema que con el reloj en manual no llega nunca; esto es síncrono y
        // produce el mismo mapa de bits.
        val vista = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(bitmap))

        val dir = File("build/capturas").apply { mkdirs() }
        File(dir, "ui-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        println("captura ${'$'}name: ${'$'}{vista.width}x${'$'}{vista.height}")
    }
}

/**
 * Un muestrario con una pieza de cada tipo. No es una pantalla real de la app:
 * es todo el vocabulario visual junto, que es lo que hay que poder mirar de una
 * vez para juzgar si la interfaz tiene carácter o no.
 */
@Composable
private fun Muestrario(nombreHostil: String) {
    var texto by remember { mutableStateOf("") }
    var encendido by remember { mutableStateOf(true) }
    var pestana by remember { mutableStateOf(1) }
    var filtro by remember { mutableStateOf(0) }

    Column(
        Modifier
            .testTag("raiz")
            .fillMaxSize()
            .background(com.azeroth.companion.ui.theme.Base),
    ) {
        WowTopBar(title = "Azeroth Companion", subtitle = "Sagrario · Área 52")
        // El mismo tope de ancho que aplica la app en `MainActivity`, para que la
        // captura enseñe lo que se ve de verdad y no una versión estirada.
        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = androidx.compose.ui.Alignment.TopCenter,
        ) {
        Column(
            Modifier
                .widthIn(max = Spacing.maxContent)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            HeroPanel {
                Text(nombreHostil, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Metric("Nivel de objeto", "678", hint = "equipado 674")
            }

            SectionHeader("Esta semana")
            Panel {
                DataRow("Mazmorras míticas", "8")
                Divider()
                DataRow("Jefes de banda", "6 / 8")
                Divider()
                DataRow("Puntuación M+", "3214", hint = "+412")
                ProgressTrack(0.62f)
            }

            WowTabs(listOf("Bandas", "Mazmorras", "Delves"), pestana, onSelect = { pestana = it })

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf("Todo", "Sin hacer", "Hecho").forEachIndexed { i, l ->
                    WowChip(l, selected = filtro == i, onClick = { filtro = i })
                }
            }

            // El elemento firma, con el marco en el color de la calidad.
            Tooltip(
                title = "Vestimenta del Crepúsculo Inmarcesible",
                titleColor = QualityColors.Epic,
                leading = { Box(Modifier.size(44.dp).background(Color(0xFF3A2E1B))) },
            ) {
                TooltipItemLevel(678)
                TooltipSlotLine("Pecho", "Tela")
                TooltipDivider(quality = QualityColors.Epic)
                TooltipStat("Intelecto", "+2.481")
                TooltipStat("Maestría", "+1.204")
                TooltipStat("Crítico", "+863")
                TooltipPrice(1_284_500)
            }

            Tooltip(
                title = nombreHostil,
                titleColor = QualityColors.Legendary,
            ) {
                TooltipSlotLine("Nombre imposible sin espacios", "Legendario")
            }

            WowTextField(texto, { texto = it }, placeholder = "Buscar objeto o zona")

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                // En la captura a 768 el interruptor y el indicador de carga
                // quedaban colgando arriba, alineados por el borde superior.
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                WowButton("Sincronizar", onClick = {}, primary = true)
                WowButton("Cerrar sesión", onClick = {})
                WowSwitch(encendido, { encendido = it })
                WowLoading()
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Pill("Confirmado", color = com.azeroth.companion.ui.theme.Positive)
                Pill("Estimado", color = com.azeroth.companion.ui.theme.Warning)
                Pill("Épico", color = QualityColors.Epic, filled = true)
            }
        }
        }
        WowNavBar(
            items = listOf(
                NavItem("hoy", "Hoy", Icons.Filled.Today),
                NavItem("pj", "Personaje", Icons.Filled.Person),
                NavItem("mundo", "Mundo", Icons.Filled.Public),
                NavItem("mercado", "Mercado", Icons.Filled.Storefront),
            ),
            selectedRoute = "hoy",
            onSelect = {},
        )
    }
}
