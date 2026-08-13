package com.azeroth.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Dirección
//
// Tres intentos anteriores fallaron y conviene dejar escrito por qué, porque el
// cuarto se apoya en ello. Los dos primeros trataron de que la app PAREZCA WoW
// imitando su interfaz de escritorio —morado y degradados; luego oro, pergamino
// y filigrana—, y a 390px de ancho eso no se lee como épico sino como recargado.
// El tercero corrigió en exceso: chrome neutro, que se calla del todo. Quedó
// correcto y anónimo.
//
// El fallo común a los tres estaba en un sitio que ninguno tocó: la app seguía
// escribiendo en **Roboto**, con componentes de Material 3, esquinas de 8-12dp y
// sombras difusas. Es decir, tenía puestas las huellas de fábrica de Android por
// debajo de cualquier decisión de color. Se pueden repintar los colores tres
// veces; mientras el esqueleto sea el de la plantilla, la app se reconoce como
// plantilla. El detector `npm run check:ui` lo midió: 61 hallazgos.
//
// La dirección de esta versión, entonces, no es de color sino de **materia**:
//
//  - Un solo material, metal biselado, dibujado con filos de un píxel y sombra
//    de desenfoque cero (`Metal.kt`). Nada flota.
//  - Esquina dura. El rango 3-14dp es el del aspecto de plantilla.
//  - Tipografía propia y empaquetada (`Type.kt`), con la lapidaria reservada a
//    los títulos y monoespaciada tabular en toda cifra que se compare.
//  - Movimiento lineal y corto, como un cooldown; nunca `ease-in-out`.
//  - El color lo pone el juego: tu clase, las calidades de objeto, el arte.
//    La interfaz que lo envuelve es gris frío y no compite.
// ---------------------------------------------------------------------------

// La rampa del fondo: azul de noche, no gris.
//
// Primero se rebajó por contraste —con el panel elevado en #20232C el rojo
// oficial de Caballero de la Muerte se quedaba en 2,69:1, por debajo del 3:1
// que se exige a un elemento gráfico—. Eso resolvió la legibilidad, pero dejó
// una rampa de gris prácticamente neutro, y en el móvil se veía apagada: negro
// plano con un solo color encima. Daniel lo dijo directamente.
//
// El arreglo NO es aclararla, que rompería el contraste otra vez. Es darle
// **croma a la misma luminancia**: el mismo brillo, pero desplazado hacia el
// azul índigo. Un gris neutro se lee como "fondo de aplicación"; el mismo valor
// con algo de azul se lee como noche, que es donde pasa medio World of Warcraft.
// Sale gratis en contraste —la peor clase sigue en 3,04 sobre el panel elevado y
// 3,40 sobre el fondo, igual que antes— y cambia por completo la sensación.
internal val Base = Color(0xFF070911)        // fondo
internal val Surface = Color(0xFF0D111E)     // panel
internal val SurfaceHigh = Color(0xFF131728) // panel elevado, fila resaltada
internal val Line = Color(0xFF232941)        // separadores

internal val TextHigh = Color(0xFFECEDF0)
internal val TextMid = Color(0xFFA3A8B4)
internal val TextLow = Color(0xFF6C7280)

internal val Positive = Color(0xFF4ADE80)
internal val Warning = Color(0xFFFBBF24)
internal val Danger = Color(0xFFF87171)

// El dorado, para recompensas y dinero, que es donde el juego lo usa. Se subió
// la saturación: el anterior tiraba a mostaza sobre el fondo azulado nuevo, y el
// oro de WoW es más cálido y más vivo.
internal val Gold = Color(0xFFF5C542)

// Nombres que el resto del código todavía usa.
internal val Arcane = ClassColors.Unknown

/**
 * Color de la clase del personaje activo. Es el acento de toda la interfaz:
 * botones, enlaces, barras de progreso y el borde del retrato.
 */
val LocalAccent = staticCompositionLocalOf { ClassColors.Unknown }

private fun scheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF070911),
    primaryContainer = accent.copy(alpha = 0.16f),
    onPrimaryContainer = accent,
    inversePrimary = accent.copy(alpha = 0.6f),
    secondary = Gold,
    onSecondary = Color(0xFF1B1405),
    secondaryContainer = Color(0xFF2A2110),
    onSecondaryContainer = Gold,
    tertiary = accent,
    background = Base,
    onBackground = TextHigh,
    surface = Surface,
    onSurface = TextHigh,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextMid,
    surfaceContainerLowest = Base,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = Color(0xFF1A2036),
    outline = Line,
    outlineVariant = Color(0xFF171C2E),
    scrim = Color(0xCC000000),
    error = Danger,
    onError = Color(0xFF2A0906),
    errorContainer = Color(0xFF3B1513),
    onErrorContainer = Color(0xFFFECACA),
)

// La tipografía vive en `Type.kt`. Aquí había una copia declarada sin
// `fontFamily`, que es justamente lo que hacía que toda la app se pintara en
// Roboto por mucho que el resto del tema cambiara.

@Composable
fun AzerothTheme(
    /** Clase del personaje activo; de ella sale el acento de toda la app. */
    accent: Color = ClassColors.Unknown,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = scheme(accent),
            typography = AppTypography,
            content = content,
        )
    }
}
