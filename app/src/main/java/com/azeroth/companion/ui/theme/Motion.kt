package com.azeroth.companion.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Movimiento reducido.
 *
 * `docs/UI-WARCRAFT.md` exige respetar `prefers-reduced-motion`. Esa consulta de
 * medios es de navegador; en Android el ajuste equivalente es la **escala de
 * duración de animaciones** de Opciones de desarrollador y de accesibilidad,
 * que el usuario pone a cero cuando el movimiento le marea o le distrae.
 *
 * Aquí importa más de lo normal porque el indicador de carga parpadea en bucle
 * mientras haya datos en camino: para alguien sensible al movimiento, eso es una
 * luz intermitente en pantalla durante segundos.
 *
 * Cuando está activo:
 *  - Las transiciones duran 0 y saltan directas al estado final. **No se
 *    eliminan**: el hundido del botón y la posición de un control siguen
 *    cambiando, porque son la respuesta al gesto; lo que desaparece es el
 *    recorrido, no el cambio.
 *  - El indicador de carga deja de parpadear y se queda fijo.
 */
@Composable
@ReadOnlyComposable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return scale == 0f
}
