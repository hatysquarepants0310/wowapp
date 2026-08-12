package com.azeroth.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.azeroth.companion.ui.components.Spacing
import com.azeroth.companion.ui.components.WowButton
import com.azeroth.companion.ui.components.WowChip
import com.azeroth.companion.ui.components.WowIconButton
import com.azeroth.companion.ui.components.WowSwitch
import com.azeroth.companion.ui.components.WowTabs
import com.azeroth.companion.ui.components.WowTextButton
import com.azeroth.companion.ui.components.WowTextField
import com.azeroth.companion.ui.theme.AzerothTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Objetivos táctiles.
 *
 * Todo lo que se pulsa tiene que medir al menos 44dp de alto. Es el mínimo por
 * debajo del cual el dedo falla, y es fácil de romper sin darse cuenta al
 * construir controles a mano: los de Material lo traían puesto, los propios hay
 * que medirlos. De ahí esta prueba.
 *
 * El chip es la excepción declarada: 34dp de alto. Va en filas de filtros donde
 * caben cinco o seis, y a 44dp cada uno la fila se come media pantalla en un
 * móvil. Lleva separación entre chips para que el área efectiva no se solape con
 * la del vecino.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-rES-w390dp-h844dp-xhdpi")
class TouchTargetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun alto(tag: String): Int {
        val nodo = compose.onNodeWithTag(tag).fetchSemanticsNode()
        return with(compose.density) { nodo.size.height.toDp().value.toInt() }
    }

    @Test
    fun `todo lo pulsable llega a 44dp de alto`() {
        compose.setContent {
            AzerothTheme {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    WowButton("Sincronizar", onClick = {}, modifier = Modifier.testTag("boton"))
                    WowTextButton("Ver más", onClick = {}, modifier = Modifier.testTag("texto"))
                    WowIconButton(
                        Icons.Filled.Settings,
                        contentDescription = "Ajustes",
                        onClick = {},
                        modifier = Modifier.testTag("icono"),
                    )
                    WowSwitch(true, {}, modifier = Modifier.testTag("casilla"))
                    WowTextField("", {}, modifier = Modifier.testTag("campo"))
                    WowTabs(
                        listOf("Uno", "Dos"),
                        0,
                        onSelect = {},
                        modifier = Modifier.testTag("pestanas"),
                    )
                }
            }
        }

        for (tag in listOf("boton", "texto", "icono", "casilla", "campo", "pestanas")) {
            val h = alto(tag)
            assert(h >= 44) { "$tag mide ${h}dp de alto, por debajo del mínimo de 44dp" }
        }
    }

    @Test
    fun `el chip se queda en su excepción declarada de 34dp`() {
        compose.setContent {
            AzerothTheme {
                WowChip("Todo", selected = true, onClick = {}, modifier = Modifier.testTag("chip"))
            }
        }
        val h = alto("chip")
        assert(h >= 34) { "El chip mide ${h}dp, por debajo incluso de su propia excepción" }
    }
}
