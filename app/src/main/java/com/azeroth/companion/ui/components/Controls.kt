package com.azeroth.companion.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.azeroth.companion.ui.theme.Base
import com.azeroth.companion.ui.theme.Line
import com.azeroth.companion.ui.theme.LocalAccent
import com.azeroth.companion.ui.theme.Surface
import com.azeroth.companion.ui.theme.SurfaceHigh
import com.azeroth.companion.ui.theme.TextHigh
import com.azeroth.companion.ui.theme.TextMid
import com.azeroth.companion.ui.theme.darken
import com.azeroth.companion.ui.theme.inset
import com.azeroth.companion.ui.theme.metal
import com.azeroth.companion.ui.theme.readableOn
import com.azeroth.companion.ui.theme.reducedMotion

/**
 * Controles propios, construidos desde primitivas sin apariencia.
 *
 * El detector confirmó el diagnóstico de `docs/UI-WARCRAFT.md` §0 en su forma
 * de Compose: la app estaba hecha con componentes de Material 3 —`Button`,
 * `Card`, `OutlinedTextField`, `Switch`, `NavigationBar`, `Tab`, `FilterChip`—
 * que traen puesta la silueta de Google. Se les puede cambiar el color y el
 * radio, pero la altura, el ripple, la forma y el gesto de foco se quedan, y
 * **eso** es lo que la gente reconoce sin saber que lo reconoce.
 *
 * Aquí cada control se dibuja desde `Box`, `Row` y `Modifier.clickable`, que no
 * aportan ninguna apariencia. Reglas que cumplen todos:
 *
 *  - **Esquina viva**. El rango 3-14dp es el del aspecto de plantilla.
 *  - **Bisel con filos**, nunca sombra difusa. Nada flota.
 *  - **Foco visible**: borde de 3dp con separación, jamás sin sustituto.
 *  - **44dp de alto mínimo**, que es el objetivo táctil.
 *  - **Movimiento lineal y seco**, como los cooldowns del juego. Ni un `ease`.
 */

/** Duración del hundido al pulsar. Corta y lineal: en WoW nada hace ease. */
private const val PRESS_MS = 70

/**
 * La duración de verdad, ya considerando si el usuario pidió movimiento
 * reducido. A cero el cambio sigue ocurriendo; lo que se quita es el recorrido.
 */
@Composable
private fun pressMs(): Int = if (reducedMotion()) 0 else PRESS_MS

/** Alto táctil mínimo. Por debajo de esto el dedo falla. */
val TouchTarget: Dp = 44.dp

/** Grosor del anillo de foco. 3dp para que se vea en un móvil a pleno sol. */
private val FOCUS = 3.dp

/**
 * Anillo de foco. Existe como función porque **desactivar el ripple sin poner
 * nada en su lugar deja la app inutilizable con teclado o mando**, y ese es un
 * fallo de accesibilidad, no de estilo. Cada `indication = null` de este archivo
 * va emparejado con una llamada a esto.
 */
private fun Modifier.focusRing(focused: Boolean, color: Color): Modifier =
    if (focused) this.border(FOCUS, color).padding(FOCUS) else this

@Composable
fun WowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val accent = LocalAccent.current
    // El rótulo es texto pequeño teñido, no una marca de identidad: se aclara lo
    // justo para leerse. La chapa de debajo conserva el tono exacto.
    val accentText = accent.readableOn(SurfaceHigh)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    // El botón se HUNDE al pulsarlo, no cambia de sombra: es una pieza física.
    val sink by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(pressMs(), easing = LinearEasing),
        label = "sink",
    )
    val base = if (primary) accent.darken(0.38f) else SurfaceHigh

    Box(
        modifier
            .defaultMinSize(minHeight = TouchTarget)
            .offset(y = sink.dp)
            .focusRing(focused, accent)
            .metal(if (enabled) base else base.darken(0.06f))
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interaction,
                // Sin ripple de Material: el sustituto es el hundido al pulsar
                // más el anillo de foco de arriba, así que la indicación existe.
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> TextMid
                primary -> accentText
                else -> TextHigh
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Acción secundaria: solo texto subrayado por un filo. Sustituye a `TextButton`,
 * que en Material es una caja invisible con ripple circular.
 */
@Composable
fun WowTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalAccent.current,
) {
    val texto = color.readableOn(Base)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .defaultMinSize(minHeight = TouchTarget)
            .focusRing(focused, color)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = texto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Botón de solo icono, con el mismo objetivo táctil que los demás. */
@Composable
fun WowIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextMid,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalAccent.current
    Box(
        modifier
            .size(TouchTarget)
            .focusRing(focused, accent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * Casilla de verificación.
 *
 * Se llamaba `WowSwitch` y dibujaba una corredera; la captura a 768 dejó claro
 * que no funcionaba. Se leía como un cuadrado rojo dentro de una caja roja: la
 * corredera llenaba el canal entero, así que no había canal, y encendida iba
 * teñida del mismo acento que el fondo, así que tampoco destacaba. Un control
 * cuyo estado hay que adivinar está roto, por bonito que sea el bisel.
 *
 * Pero el fallo de fondo era la metáfora. **El interruptor deslizante es de
 * iOS y de Material; en World of Warcraft no existe.** Todas las opciones del
 * juego son casillas: un hueco cuadrado hundido y, al marcarlo, una palomita.
 * Así que esto es una casilla — más legible y, encima, la forma correcta.
 *
 * Conserva el nombre y la firma para no tocar los sitios que ya la usan.
 */
@Composable
fun WowSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accent = LocalAccent.current
    val marca = accent.readableOn(Surface)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(TouchTarget)
            .focusRing(focused, accent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).inset(Surface), contentAlignment = Alignment.Center) {
            if (checked) {
                Canvas(Modifier.size(24.dp)) {
                    val g = size.width * 0.22f
                    val grosor = size.width * 0.14f
                    // Dos trazos rectos, sin curva: la palomita del juego es
                    // angulosa, no la marca redondeada de Material.
                    drawLine(
                        color = marca,
                        start = Offset(g, size.height * 0.52f),
                        end = Offset(size.width * 0.44f, size.height - g),
                        strokeWidth = grosor,
                    )
                    drawLine(
                        color = marca,
                        start = Offset(size.width * 0.44f, size.height - g),
                        end = Offset(size.width - g, g),
                        strokeWidth = grosor,
                    )
                }
            }
        }
    }
}

/** Campo de texto: un hueco hundido con filo de luz abajo. */
@Composable
fun WowTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = TouchTarget)
            .then(if (focused) Modifier.border(2.dp, accent) else Modifier)
            .inset(Surface)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Spacing.sm))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = TextMid)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextHigh),
                cursorBrush = SolidColor(accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = keyboardType,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.sm))
            trailing()
        }
    }
}

/**
 * Barra de progreso lineal. Alias de [ProgressTrack] para las llamadas que
 * venían de `LinearProgressIndicator`.
 */
@Composable
fun WowProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = LocalAccent.current,
    height: Dp = 8.dp,
) = ProgressTrack(fraction, modifier, color, height)

/**
 * Indicador de carga: cuatro casillas que se encienden por turnos, como el pulso
 * de un addon. Sin giro suave, que es el gesto de Material y el que hace que
 * cualquier pantalla de espera se vea igual en cualquier app.
 */
@Composable
fun WowLoading(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    if (reducedMotion()) {
        // Sin parpadeo: para quien pidió movimiento reducido, un indicador que
        // late en bucle es justo lo que quería evitar. Se queda encendido el
        // primero, que sigue diciendo "esto está trabajando".
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(4) { i ->
                Box(Modifier.size(6.dp).background(if (i == 0) accent else Line))
            }
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "load")
    // Se anima un flotante y se trunca a casilla. Con `animateValue` sobre Int
    // el convertidor interpola y redondea, y el salto se vuelve irregular; así
    // el paso cae siempre a intervalos iguales, que es lo que hace que se lea
    // como un pulso y no como una animación.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(animation = tween(720, easing = LinearEasing)),
        label = "step",
    )
    val step = phase.toInt().coerceIn(0, 3)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(4) { i ->
            Box(Modifier.size(6.dp).background(if (i == step) accent else Line))
        }
    }
}

/**
 * Pestañas: casillas contiguas separadas por un filo, con la activa marcada por
 * una regla de acento **abajo** y el resto hundidas. Es la barra de pestañas del
 * libro de hechizos, no el `TabRow` de Material con su subrayado que se desliza.
 */
@Composable
fun WowTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    val accent = LocalAccent.current
    // Con pocas pestañas se reparten a partes iguales; con muchas, la fila rueda
    // de lado. Cuál de las dos lo decide quien llama, porque depende del texto:
    // "Bandas / Mazmorras / Delves" cabe, y catorce temporadas no.
    val outer =
        if (scrollable) modifier.horizontalScroll(rememberScrollState()) else modifier.fillMaxWidth()
    Row(outer, horizontalArrangement = Arrangement.spacedBy(Spacing.hairline)) {
        tabs.forEachIndexed { i, label ->
            val active = i == selected
            var focused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .then(if (scrollable) Modifier else Modifier.weight(1f))
                    .defaultMinSize(minHeight = TouchTarget)
                    .focusRing(focused, accent)
                    .onFocusChanged { focused = it.isFocused }
                    .then(if (active) Modifier.metal(SurfaceHigh) else Modifier.inset(Surface))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(i) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) accent.readableOn(SurfaceHigh) else TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (active) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(accent),
                    )
                }
            }
        }
    }
}

/**
 * Filtro seleccionable. Placa rectangular con filo, no la píldora de Material
 * con su palomita animada que aparece por la izquierda.
 */
@Composable
fun WowChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalAccent.current,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .defaultMinSize(minHeight = 34.dp)
            .focusRing(focused, color)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (selected) {
                    Modifier.background(color.copy(alpha = 0.18f)).border(Spacing.hairline, color)
                } else {
                    Modifier.background(SurfaceHigh).border(Spacing.hairline, Line)
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) color.readableOn(SurfaceHigh) else TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Diálogo. Una chapa con regla de acento arriba, sobre un velo casi opaco. El
 * `AlertDialog` de Material llega con esquina de 28dp, elevación difusa y sus
 * botones de texto alineados a la derecha: es reconocible al instante.
 */
@Composable
fun WowDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    body: @Composable () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val accent = LocalAccent.current
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
        ),
    ) {
        Column(modifier.metal(Surface)) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(accent))
            Column(Modifier.padding(Spacing.lg)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextHigh,
                )
                Spacer(Modifier.height(Spacing.md))
                body()
                Spacer(Modifier.height(Spacing.lg))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

/**
 * Barra superior. Franja de metal con filo inferior; el `TopAppBar` de Material
 * trae 64dp de alto, su tipografía y su comportamiento de scroll.
 */
@Composable
fun WowTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    // La chapa ocupa todo el ancho, pero su contenido se ciñe al mismo tope que
    // la columna. Con el título pegado al borde izquierdo de una pantalla de
    // 1440 y el contenido centrado, la barra parecía de otra aplicación.
    Box(modifier.fillMaxWidth().metal(Surface), contentAlignment = Alignment.TopCenter) {
    Row(
        Modifier
            .widthIn(max = Spacing.maxContent)
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigation?.invoke()
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
    }
}

/**
 * Barra de navegación: casillas de barra de acción. La activa se marca con un
 * filo de acento arriba y fondo teñido, que es como el juego señala la acción
 * en curso; `NavigationBar` marca con una píldora que crece detrás del icono.
 */
@Composable
fun WowNavBar(
    items: List<NavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    Box(modifier.fillMaxWidth().metal(Surface), contentAlignment = Alignment.TopCenter) {
    Row(Modifier.widthIn(max = Spacing.maxContent).fillMaxWidth()) {
        items.forEach { item ->
            val active = item.route == selectedRoute
            var focused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .focusRing(focused, accent)
                    .onFocusChanged { focused = it.isFocused }
                    .then(
                        if (active) Modifier.background(accent.copy(alpha = 0.10f)) else Modifier,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(item.route) }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(accent),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = if (active) accent else TextMid,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) accent.readableOn(Surface) else TextMid,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    }
}

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** El velo que hay detrás de una hoja modal. Casi opaco: el fondo no distrae. */
internal val Scrim = Base.copy(alpha = 0.88f)
