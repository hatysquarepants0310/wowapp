package com.azeroth.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vocabulario visual de la app.
 *
 * El diseño anterior envolvía cada cosa en su propia tarjeta, así que la
 * pantalla se leía como una pila de cajas todas del mismo peso y no se sabía
 * qué mirar primero. Aquí la jerarquía la marcan el espacio, el tamaño del
 * texto y una línea de separación fina; el panel con fondo se reserva para lo
 * que de verdad es una unidad (una rejilla, una fila de datos), no para cada
 * párrafo.
 *
 * Reglas:
 *  - Un solo acento por pantalla, en lo que el usuario debe mirar primero.
 *  - Las cifras mandan: grandes, con peso, y la etiqueta pequeña encima.
 *  - Nada de bordes salvo para separar; la profundidad viene del fondo.
 */
object Spacing {
    val hairline: Dp = 1.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /**
     * Margen lateral único de toda la app.
     *
     * Baja de 20dp a 14dp a propósito: los jugadores de WoW vienen de ElvUI y
     * Details! y leen interfaces densas. Tres datos rodeados de aire no se leen
     * como elegantes, se leen como una app que no sabe lo que estás haciendo.
     */
    val gutter: Dp = 14.dp
}

object Radius {
    // Radios bajos y borde en vez de sombra: es el trim metálico del juego, no
    // la tarjeta redondeada con sombra difusa de una plantilla de dashboard.
    val sm: Dp = 4.dp
    val md: Dp = 6.dp
    val lg: Dp = 8.dp
    val pill: Dp = 999.dp
}

/** Contenedor raíz de una pantalla con scroll perezoso y márgenes coherentes. */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = Spacing.gutter,
        end = Spacing.gutter,
        top = Spacing.sm,
        bottom = Spacing.xxl,
    ),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        content = content,
    )
}

/**
 * Cabecera de sección: etiqueta en versalita a la izquierda y, si hace falta,
 * una acción discreta a la derecha. Sustituye al título dentro de una tarjeta.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.md, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = com.azeroth.companion.ui.theme.OroTabardo,
            )
            // Filete: es como el juego separa secciones, y evita que la etiqueta
            // flote sola en medio de la nada.
            Spacer(Modifier.width(Spacing.sm))
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable(onClick = onAction)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
    }
}

/**
 * Superficie agrupadora. Solo para contenido que forma una unidad; si dentro
 * hay una sola frase, esa frase no necesita panel.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    tone: PanelTone = PanelTone.Default,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = when (tone) {
        PanelTone.Default -> colors.surface
        PanelTone.Raised -> colors.surfaceContainer
        PanelTone.Accent -> colors.primaryContainer
        PanelTone.Warning -> colors.errorContainer
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(background)
            // Profundidad por borde, nunca por sombra difusa.
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.md))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

enum class PanelTone { Default, Raised, Accent, Warning }

/** Línea de separación apenas perceptible: ordena sin dibujar cajas. */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Spacing.hairline)
            .background(MaterialTheme.colorScheme.outline),
    )
}

/**
 * La cifra protagonista de un bloque, con su etiqueta encima en pequeño. Es el
 * patrón que hace que un dato se lea de un vistazo.
 */
@Composable
fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    accent: Color? = null,
) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
        )
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Fila de datos tabular: etiqueta a la izquierda, cifra alineada a la derecha.
 *
 * Es lo que sustituye a las tarjetas de métrica gigantes. Tres cifras enormes
 * ocupaban media pantalla; así caben seis y se comparan de un vistazo, que es
 * como lee esta gente.
 */
@Composable
fun DataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    hint: String? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Fila de contenido pulsable: título, apoyo y valor a la derecha. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    leading: (@Composable () -> Unit)? = null,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = accent ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.md))
            Text(
                trailing,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Etiqueta compacta. El color lo pone quien la usa, no hay uno por defecto. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    filled: Boolean = false,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (filled) MaterialTheme.colorScheme.onPrimary else color,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = Spacing.sm, vertical = 3.dp),
    )
}

/** Barra de progreso plana, sin sombra ni relieve. */
@Composable
fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 6.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(Radius.pill))
                .background(color),
        )
    }
}

/**
 * Cabecera grande de pantalla: título en display y una línea de contexto.
 * Sustituye a la barra de aplicación cuando la pantalla es un destino raíz.
 */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** Estado vacío: dice qué falta y qué hacer, no solo "sin datos". */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(Spacing.md))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (detail != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Franja destacada de la parte superior de Inicio: lo único de la app que se
 * permite un degradado, porque es el ancla visual de la pantalla.
 */
@Composable
fun HeroPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(
                Brush.linearGradient(
                    listOf(colors.primaryContainer, colors.surfaceContainer),
                ),
            )
            .padding(Spacing.xl),
        content = content,
    )
}

/** Punto de estado. Un color y tres píxeles dicen más que una palabra. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Cifra con su unidad pequeña al lado, alineadas por la base. */
@Composable
fun ValueWithUnit(
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
        Spacer(Modifier.width(3.dp))
        Text(
            unit,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}
