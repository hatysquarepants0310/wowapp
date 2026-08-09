package com.azeroth.companion.feature.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    viewModel: LiveMapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedZone by remember { mutableIntStateOf(0) }
    var focusedPin by remember { mutableStateOf<MapPin?>(null) }

    Screen {
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
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
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
            items(zone.pins.size) { index ->
                val pin = zone.pins[index]
                Column {
                    ListRow(
                        title = pin.name,
                        subtitle = "%.1f, %.1f".format(pin.x, pin.y),
                        leading = {
                            StatusDot(pinColor(pin.kind), size = 10.dp)
                        },
                        onClick = { onOpenQuest(pin.questId) },
                    )
                    if (index < zone.pins.lastIndex) Divider()
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
 * de las tablas QuestPOI del cliente, las mismas que usa TomTom, así que la
 * posición relativa de cada punto dentro de la zona es la de verdad.
 */
@Composable
private fun ZoneMap(zone: LiveZone, focused: MapPin?, onPinTap: (MapPin) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val grid = colors.outline
    val dotRadius = 7f

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 2f)
            .clip(RoundedCornerShape(Radius.md))
            .background(colors.surface),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .pointerInput(zone.uiMapId) {
                    detectTapGestures { tap ->
                        // Se elige el punto más cercano dentro de un radio
                        // generoso: los dedos no son ratones.
                        val hit = zone.pins.minByOrNull { pin ->
                            val px = (pin.x / 100.0).toFloat() * size.width
                            val py = (pin.y / 100.0).toFloat() * size.height
                            (tap - Offset(px, py)).getDistanceSquared()
                        }
                        if (hit != null) {
                            val px = (hit.x / 100.0).toFloat() * size.width
                            val py = (hit.y / 100.0).toFloat() * size.height
                            if ((tap - Offset(px, py)).getDistance() < 64f) onPinTap(hit)
                        }
                    }
                },
        ) {
            // Rejilla de referencia: da noción de escala sin fingir un mapa.
            val step = size.width / 10f
            for (i in 1 until 10) {
                drawLine(grid, Offset(step * i, 0f), Offset(step * i, size.height), 1f)
            }
            val vStep = size.height / 7f
            for (i in 1 until 7) {
                drawLine(grid, Offset(0f, vStep * i), Offset(size.width, vStep * i), 1f)
            }

            zone.pins.forEach { pin ->
                val center = Offset(
                    (pin.x / 100.0).toFloat() * size.width,
                    (pin.y / 100.0).toFloat() * size.height,
                )
                val color = pinColor(pin.kind)
                drawCircle(color.copy(alpha = 0.22f), dotRadius * 2.4f, center)
                drawCircle(color, dotRadius, center)
                if (pin.questId == focused?.questId) {
                    drawCircle(color, dotRadius * 2.2f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                }
            }
        }

        Text(
            zone.name,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.md),
        )

        if (focused != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.md)
                    .clip(RoundedCornerShape(Radius.sm))
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
