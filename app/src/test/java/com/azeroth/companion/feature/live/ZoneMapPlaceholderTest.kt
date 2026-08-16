package com.azeroth.companion.feature.live

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.azeroth.companion.core.map.ZoneMapArtState
import com.azeroth.companion.data.LiveZone
import com.azeroth.companion.data.MapPin
import com.azeroth.companion.data.PinKind
import com.azeroth.companion.ui.theme.AzerothTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Primera pintura: nombre de zona + mensaje de carga (o error), nunca un
 * plano vacío. Los pines de la lista no se prueban aquí: viven fuera de
 * [ZoneMap] y no esperan al bitmap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-rES-w390dp-h844dp-xhdpi")
class ZoneMapPlaceholderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone = LiveZone(
        uiMapId = 84,
        name = "Orgrimmar",
        pins = listOf(
            MapPin(1, "Misión de prueba", 50.0, 50.0, 84, PinKind.ACTIVE_QUEST),
        ),
    )

    @Test
    fun `primera pintura muestra zona y cargando mapa`() {
        compose.setContent {
            AzerothTheme {
                ZoneMap(
                    zone = zone,
                    art = null,
                    artState = ZoneMapArtState.Loading,
                    focused = null,
                    onPinTap = {},
                )
            }
        }
        compose.onNodeWithText("Orgrimmar").assertIsDisplayed()
        compose.onNodeWithText("Cargando mapa del juego…").assertIsDisplayed()
    }

    @Test
    fun `tile fallido muestra error y el nombre de la zona`() {
        compose.setContent {
            AzerothTheme {
                ZoneMap(
                    zone = zone,
                    art = null,
                    artState = ZoneMapArtState.Failed("No se pudo cargar el mapa de esta zona."),
                    focused = null,
                    onPinTap = {},
                )
            }
        }
        compose.onNodeWithText("Orgrimmar").assertIsDisplayed()
        compose.onNodeWithText("No se pudo cargar el mapa de esta zona.").assertIsDisplayed()
    }
}
