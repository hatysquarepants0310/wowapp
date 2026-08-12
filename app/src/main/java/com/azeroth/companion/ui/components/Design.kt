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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azeroth.companion.ui.theme.Base
import com.azeroth.companion.ui.theme.BigNumberStyle
import com.azeroth.companion.ui.theme.NumberStyle
import com.azeroth.companion.ui.theme.LocalAccent
import com.azeroth.companion.ui.theme.Surface
import com.azeroth.companion.ui.theme.SurfaceHigh
import com.azeroth.companion.ui.theme.darken
import com.azeroth.companion.ui.theme.inset
import com.azeroth.companion.ui.theme.metal

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
     * Margen lateral único de toda la app. 16dp: ni el aire de plantilla ni el
     * agobio del panel de addon. La densidad se consigue con filas de datos,
     * no estrangulando el margen.
     */
    val gutter: Dp = 16.dp

    /**
     * Ancho máximo de una columna de contenido.
     *
     * Esto salió de mirar las capturas. A 1440dp la interfaz no desbordaba —la
     * comprobación de desbordamiento pasaba— pero una fila de datos ponía
     * "Mazmorras míticas" en el extremo izquierdo y su "8" a casi dos mil
     * píxeles a la derecha. Técnicamente correcto e ilegible: el ojo no une la
     * etiqueta con su cifra, y una tabla que no se puede recorrer con la mirada
     * ha dejado de ser una tabla.
     *
     * Además, una única columna estirada a lo ancho de la pantalla es en sí
     * misma una señal de app genérica: es lo que pasa cuando se hace un diseño
     * de móvil y no se vuelve a mirar en grande.
     *
     * 560dp es donde una fila de etiqueta y cifra sigue leyéndose de un vistazo.
     * El juego hace lo mismo: sus marcos son de tamaño fijo y centrados, no se
     * estiran con la resolución.
     */
    val maxContent: Dp = 560.dp
}

/**
 * Radios.
 *
 * Los valores de antes —8, 12, 16dp— son exactamente el rango que el detector
 * marca como aspecto de plantilla, y con razón: es el radio que traen por
 * defecto Material, shadcn, Bootstrap y cualquier librería de componentes. Una
 * pantalla llena de rectángulos de esquina blanda a 12dp se reconoce como "app
 * de móvil genérica" antes de que el ojo llegue a leer nada.
 *
 * En World of Warcraft no hay una sola esquina redondeada: los marcos son
 * chapas rectangulares y el único elemento curvo del juego es el retrato, que es
 * un círculo completo. Así que aquí solo hay dos radios de verdad —cero y
 * círculo— y un `soft` de 2dp para matar el diente de sierra en piezas
 * diminutas.
 */
object Radius {
    /** Esquina viva. Lo normal. */
    val none: Dp = 0.dp

    /** 2dp: solo para quitar el aliasing de piezas de menos de 24dp. */
    val soft: Dp = 2.dp

    /** Círculo. Reservado al retrato y a los puntos de estado. */
    val round: Dp = 999.dp
}

/**
 * Contenedor raíz de una pantalla con scroll perezoso y márgenes coherentes.
 *
 * La columna se limita a [Spacing.maxContent] y se centra. Ver el porqué en la
 * nota de ese token: en pantalla ancha, estirar salía peor que ceñir.
 */
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
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = modifier.fillMaxSize().widthIn(max = Spacing.maxContent),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content,
        )
    }
}

/**
 * La misma restricción de ancho para pantallas que no usan [Screen] porque
 * llevan su propio scroll o su propio andamiaje.
 */
@Composable
fun ContentColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(modifier.widthIn(max = Spacing.maxContent), content = content)
    }
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
            .padding(top = Spacing.lg, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                            .clickable(onClick = onAction)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
    }
}

/**
 * Superficie agrupadora: una **chapa** de metal biselada. Solo para contenido
 * que forma una unidad; si dentro hay una sola frase, esa frase no necesita
 * panel.
 *
 * Antes era un rectángulo de color plano con esquina de 12dp, que es la tarjeta
 * por defecto de cualquier librería. Ahora es la misma pieza física que el resto
 * de la app: degradado corto, filo de luz arriba, filo de sombra abajo y
 * asiento sólido —sin desenfoque— debajo. Es la diferencia entre un rectángulo
 * dibujado y un objeto apoyado.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    tone: PanelTone = PanelTone.Default,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = LocalAccent.current
    val base = when (tone) {
        PanelTone.Default -> Surface
        PanelTone.Raised -> SurfaceHigh
        PanelTone.Accent -> accent.darken(0.42f)
        PanelTone.Warning -> Color(0xFF3B1513)
    }
    Column(
        modifier
            .fillMaxWidth()
            .metal(base)
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
            style = BigNumberStyle,
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
            // La cifra en monoespaciada tabular. Estaba heredando la glífica del
            // cuerpo, que es proporcional: en una columna de ilvl o de oro, al
            // cambiar un dígito la cifra se desplazaba y comparar dos filas
            // —lo único que se hace en una tabla— dejaba de funcionar.
            value,
            style = NumberStyle,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
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

/**
 * Etiqueta compacta. El color lo pone quien la usa, no hay uno por defecto.
 *
 * Dejó de ser una píldora. La píldora de esquina completamente redonda es el
 * "chip" de Material y no existe en ninguna parte del juego: allí una etiqueta
 * es una placa rectangular con su filo. Sigue llamándose `Pill` para no tocar
 * treinta llamadas por un nombre.
 */
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
        color = if (filled) Base else color,
        modifier = modifier
            .background(if (filled) color else color.copy(alpha = 0.13f))
            .border(Spacing.hairline, color.copy(alpha = if (filled) 0f else 0.34f))
            .padding(horizontal = Spacing.sm, vertical = 3.dp),
    )
}

/**
 * Barra de progreso: un **canal hundido** con una barra apoyada dentro, que es
 * exactamente cómo se ve una barra de casteo o de experiencia en el juego.
 *
 * La versión anterior era una píldora de extremos redondos sobre fondo plano —
 * la barra de progreso de Material tal cual. El canal hundido (`inset`) hace el
 * trabajo que hacía el redondeo, que es separar el relleno del fondo, pero con
 * el vocabulario correcto.
 */
@Composable
fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 8.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .inset(Surface),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .metal(color.darken(0.22f), seated = false),
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
 * Franja destacada de la parte superior de Inicio.
 *
 * Era un degradado diagonal de acento a superficie: el "hero con gradiente", que
 * está en la lista de defectos del documento por delante de casi todo. Ahora es
 * la misma chapa que el resto, distinguida por **una regla de acento de 2dp
 * arriba** —el recurso con el que el juego marca el marco activo— y por su
 * tamaño. Que algo sea importante se dice con jerarquía, no con degradado.
 */
@Composable
fun HeroPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = LocalAccent.current
    Column(modifier.fillMaxWidth().metal(SurfaceHigh)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(accent))
        Column(Modifier.padding(Spacing.lg), content = content)
    }
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
        Text(value, style = BigNumberStyle, color = color)
        Spacer(Modifier.width(3.dp))
        Text(
            unit,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}
