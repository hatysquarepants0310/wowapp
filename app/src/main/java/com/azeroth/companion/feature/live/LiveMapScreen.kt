package com.azeroth.companion.feature.live

import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.ui.components.WowLoading
import com.azeroth.companion.R
import com.azeroth.companion.data.LiveZone
import com.azeroth.companion.data.MapPin
import com.azeroth.companion.data.PinKind
import com.azeroth.companion.ui.components.CountdownText
import com.azeroth.companion.ui.components.Divider
import com.azeroth.companion.ui.components.EmptyState
import com.azeroth.companion.ui.components.ListRow
import com.azeroth.companion.ui.components.Panel
import com.azeroth.companion.ui.components.PanelTone
import com.azeroth.companion.ui.components.Pill
import com.azeroth.companion.ui.components.Radius
import com.azeroth.companion.ui.components.Screen
import com.azeroth.companion.ui.components.ScreenTitle
import com.azeroth.companion.ui.components.SectionHeader
import com.azeroth.companion.ui.components.Spacing
import com.azeroth.companion.ui.components.StatusDot
import com.azeroth.companion.ui.theme.Arcane
import com.azeroth.companion.ui.theme.Gold
import com.azeroth.companion.ui.theme.Positive

@Composable
fun LiveMapScreen(
    onOpenQuest: (Int) -> Unit,
    header: (@Composable () -> Unit)? = null,
    viewModel: LiveMapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedZone by remember { mutableIntStateOf(0) }
    var focusedPin by remember { mutableStateOf<MapPin?>(null) }

    Screen {
        if (header != null) {
            item { header() }
        }
        item {
            ScreenTitle(
                stringResource(R.string.title_live),
                subtitle = state.characterName?.let {
                    stringResource(R.string.live_subtitle_character, it)
                } ?: stringResource(R.string.live_subtitle),
                trailing = {
                    Pill(
                        stringResource(R.string.live_refresh),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { viewModel.refresh() },
                    )
                },
            )
        }

        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(Spacing.xxl), Alignment.Center) {
                    WowLoading()
                }
            }
            return@Screen
        }

        // ---- Eventos con cuenta atrás real -------------------------------
        if (state.events.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.live_events)) }
            items(state.events.size) { index ->
                val event = state.events[index]
                Panel(
                    tone = if (event.active) PanelTone.Accent else PanelTone.Default,
                    padding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (event.active) Positive else MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(event.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                event.zone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (event.active) {
                            Pill(stringResource(R.string.live_now), color = Positive, filled = true)
                        } else {
                            event.startsAt?.let {
                                CountdownText(it, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }

        // ---- Mapa de la zona ---------------------------------------------
        if (state.zones.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.live_no_quests),
                    detail = state.error ?: stringResource(R.string.live_no_quests_detail),
                )
            }
        } else {
            val zone = state.zones.getOrElse(selectedZone) { state.zones.first() }
            item { SectionHeader(stringResource(R.string.live_map)) }
            if (state.zones.size > 1) {
                item {
                    ZoneChips(
                        zones = state.zones,
                        selected = selectedZone.coerceIn(0, state.zones.lastIndex),
                        onSelect = { selectedZone = it; focusedPin = null },
                    )
                }
            }
            item {
                ZoneMap(
                    zone = zone,
                    art = state.maps[zone.uiMapId],
                    loadingArt = state.loadingMaps && state.maps[zone.uiMapId] == null,
                    focused = focusedPin,
                    onPinTap = { focusedPin = it },
                )
            }
            item {
                Text(
                    stringResource(R.string.live_map_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionHeader(stringResource(R.string.live_quests_here)) }
            // El mismo orden que los números del mapa, para poder ir del punto
            // a su misión sin adivinar.
            val ordered = zone.pins.sortedBy { it.name }
            items(ordered.size) { index ->
                val pin = ordered[index]
                Column {
                    ListRow(
                        title = pin.name,
                        subtitle = "%.1f, %.1f".format(pin.x, pin.y),
                        leading = {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(pinColor(pin.kind).copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = pinColor(pin.kind),
                                )
                            }
                        },
                        onClick = { onOpenQuest(pin.questId) },
                    )
                    if (index < ordered.lastIndex) Divider()
                }
            }
        }
    }
}

private fun pinColor(kind: PinKind): Color = when (kind) {
    PinKind.WORLD_QUEST -> Gold
    PinKind.ACTIVE_QUEST -> Arcane
}

@Composable
private fun ZoneChips(zones: List<LiveZone>, selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(zones.size) { index ->
            val active = index == selected
            Pill(
                "${zones[index].name} · ${zones[index].pins.size}",
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                filled = active,
                modifier = Modifier.clickable { onSelect(index) },
            )
        }
    }
}

/**
 * El mapa.
 *
 * Se dibuja en vectorial, no con la imagen del mapa del juego: esas texturas
 * son arte con copyright de Blizzard y meterlas en un APK público sería
 * redistribuirlas. Lo que sí es información —las COORDENADAS— es exacta y sale
 * de las tablas QuestPOI del cliente, las mismas que usa TomTom.
 *
 * La primera versión era una rejilla desnuda con cuatro puntos diminutos y se
 * leía como un cuadro vacío. Ahora la rejilla lleva sus coordenadas rotuladas
 * (que es como el jugador piensa: "está en 51, 72"), los puntos van numerados
 * y con su nombre, y los que caen encima se separan para que se puedan tocar
 * por separado.
 */
@Composable
private fun ZoneMap(
    zone: LiveZone,
    art: android.graphics.Bitmap?,
    loadingArt: Boolean,
    focused: MapPin?,
    onPinTap: (MapPin) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val grid = colors.outline
    val labelColor = colors.onSurfaceVariant
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val pinLabelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface)

    // Dos misiones del mismo objetivo comparten coordenada casi exacta y se
    // dibujaban una encima de otra: se reparten en un pequeño abanico.
    val placed = remember(zone.uiMapId, zone.pins) { spread(zone.pins) }
    val hasArt = art != null

    // El mapa del juego manda en la proporción; sin arte, un 3:2 que es la
    // forma habitual de un mapa de zona.
    val ratio = art?.let { it.width.toFloat() / it.height } ?: 1.5f

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(Radius.none))
            .background(colors.surface),
    ) {
        if (art != null) {
            androidx.compose.foundation.Image(
                bitmap = art.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (loadingArt) {
            Text(
                stringResource(R.string.live_map_loading),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(zone.uiMapId) {
                    detectTapGestures { tap ->
                        val hit = placed.minByOrNull { (_, point) ->
                            val px = point.first / 100f * size.width
                            val py = point.second / 100f * size.height
                            (tap - Offset(px, py)).getDistanceSquared()
                        }
                        if (hit != null) {
                            val px = hit.second.first / 100f * size.width
                            val py = hit.second.second / 100f * size.height
                            if ((tap - Offset(px, py)).getDistance() < 72f) onPinTap(hit.first)
                        }
                    }
                },
        ) {
            // La rejilla rotulada solo tiene sentido cuando NO hay mapa: sobre
            // el arte real es ruido encima del dibujo.
            if (!hasArt) {
                for (i in 1..4) {
                    val x = size.width * i / 5f
                    val y = size.height * i / 5f
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                    val label = (i * 20).toString()
                    drawText(textMeasurer, label, Offset(x + 4f, 4f), style = labelStyle)
                    drawText(textMeasurer, label, Offset(4f, y + 2f), style = labelStyle)
                }
            }

            placed.forEachIndexed { index, (pin, point) ->
                val center = Offset(point.first / 100f * size.width, point.second / 100f * size.height)
                val color = pinColor(pin.kind)
                val isFocused = pin.questId == focused?.questId
                val radius = if (isFocused) 13f else 11f
                // Sobre el pergamino del mapa, un punto plano se pierde: un halo
                // oscuro debajo lo despega del fondo sin taparlo.
                if (hasArt) {
                    drawCircle(Color.Black.copy(alpha = 0.55f), radius * 1.9f, center)
                } else {
                    drawCircle(color.copy(alpha = 0.25f), radius * 2.1f, center)
                }
                drawCircle(color, radius, center)
                // Número dentro del punto: enlaza el mapa con la lista de abajo.
                val number = textMeasurer.measure(
                    (index + 1).toString(),
                    style = pinLabelStyle,
                )
                drawText(
                    number,
                    topLeft = Offset(
                        center.x - number.size.width / 2f,
                        center.y - number.size.height / 2f,
                    ),
                )
            }
        }

        Text(
            zone.name,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md),
        )

        if (focused != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.md)
                    .clip(RoundedCornerShape(Radius.none))
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                Column {
                    Text(focused.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "%.1f, %.1f".format(focused.x, focused.y),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(Spacing.sm))
}

/**
 * Separa los puntos que caen prácticamente en el mismo sitio.
 *
 * Varias misiones del mismo objetivo comparten coordenada (51.5,72.8 y
 * 51.4,72.9 son el mismo punto en pantalla), así que se dibujaban una sobre
 * otra: se veían cuatro puntos donde había seis misiones y era imposible tocar
 * la de debajo. Los empatados se abren en círculo alrededor del punto real.
 */
private fun spread(pins: List<MapPin>): List<Pair<MapPin, Pair<Float, Float>>> {
    val clusters = pins.groupBy { (it.x * 2).toInt() to (it.y * 2).toInt() }
    val out = mutableListOf<Pair<MapPin, Pair<Float, Float>>>()
    clusters.values.forEach { group ->
        if (group.size == 1) {
            val pin = group.first()
            out += pin to (pin.x.toFloat() to pin.y.toFloat())
        } else {
            group.forEachIndexed { index, pin ->
                val angle = 2.0 * Math.PI * index / group.size
                out += pin to (
                    (pin.x + RADIUS * kotlin.math.cos(angle)).toFloat().coerceIn(2f, 98f) to
                        (pin.y + RADIUS * kotlin.math.sin(angle)).toFloat().coerceIn(2f, 98f)
                    )
            }
        }
    }
    // El orden de la lista de abajo tiene que casar con los números del mapa.
    return out.sortedBy { it.first.name }
}

/** Separación del abanico, en unidades de mapa. */
private const val RADIUS = 2.2
