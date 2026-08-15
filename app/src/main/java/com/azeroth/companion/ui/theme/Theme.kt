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

// La rampa del fondo: negro CÁLIDO con oro. El tercer intento, y el porqué.
//
// Van tres paletas rechazadas y conviene dejarlas escritas, porque el patrón
// dice más que cualquiera de ellas por separado:
//
//   v3.0.0  casi negro + morado        -> "vibe codeado"
//   v4.1    gris neutro frío           -> "genérica"
//   v4.3.1  azul índigo                -> "sigue sin gustarme"
//
// Las tres son la misma idea: un fondo desaturado y oscuro con UN acento. Eso
// es el modo oscuro de cualquier app de 2024, no World of Warcraft. Y mientras
// tanto, en la v4.1 dejé escrito que el dorado "ya no es color de marca" y lo
// aparté a las monedas. Ese fue probablemente el momento exacto en que la app
// dejó de parecerse al juego.
//
// Porque el color que firma la interfaz de WoW es el ORO SOBRE MARRÓN OSCURO.
// Está en el marco del personaje, en la barra de acción, en el borde del
// minimapa, en cada ventana: metal dorado sobre negro cálido. No es un detalle
// decorativo, es la identidad.
//
// Ojo con no repetir el error de la v3.2.0, que también era dorada y también se
// rechazó: aquello eran TEXTURAS de pergamino y filigrana recargada imitando la
// interfaz de escritorio. Esto es otra cosa: una paleta cálida y el oro usado
// como color estructural —cabeceras, filos, cifras que importan—, sin un solo
// ornamento.
//
// Medido: la peor clase queda en 3,04 sobre el panel elevado, igual que las
// rampas anteriores, y el oro llega a 10,9:1. No se pierde nada de contraste.
internal val Base = Color(0xFF07080B)
internal val Surface = Color(0xFF0E1116)
internal val SurfaceHigh = Color(0xFF151920)
internal val Line = Color(0xFF2A3140)

internal val TextHigh = Color(0xFFECEDF0)
internal val TextMid = Color(0xFFA3A8B4)
internal val TextLow = Color(0xFF6C7280)

internal val Positive = Color(0xFF4ADE80)
internal val Warning = Color(0xFFFBBF24)
internal val Danger = Color(0xFFF87171)

// El oro. Ya no es solo el color del dinero: es el color ESTRUCTURAL de la app
// —cabeceras de sección, filos, la cifra que importa—, que es como lo usa el
// juego. Fue un error apartarlo a las monedas.
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
    onPrimary = Color(0xFF0C0906),
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
    surfaceContainerHighest = Color(0xFF1C222C),
    outline = Line,
    outlineVariant = Color(0xFF1A1F28),
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
