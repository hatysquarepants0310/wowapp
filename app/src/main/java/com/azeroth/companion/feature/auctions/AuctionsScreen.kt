package com.azeroth.companion.feature.auctions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.R
import com.azeroth.companion.core.catalog.ItemQuality
import com.azeroth.companion.core.catalog.formatGold
import com.azeroth.companion.data.AuctionListing
import com.azeroth.companion.data.AuctionScope
import com.azeroth.companion.ui.components.Divider
import com.azeroth.companion.ui.components.EmptyState
import com.azeroth.companion.ui.components.ListRow
import com.azeroth.companion.ui.components.Panel
import com.azeroth.companion.ui.components.PanelTone
import com.azeroth.companion.ui.components.Pill
import com.azeroth.companion.ui.components.Radius
import com.azeroth.companion.ui.components.Screen
import com.azeroth.companion.ui.components.ScreenTitle
import com.azeroth.companion.ui.components.Spacing
import java.time.Duration
import java.time.Instant

/** Colores de calidad del juego: es el idioma que el jugador ya tiene aprendido. */
private fun qualityColor(quality: ItemQuality?): Color = when (quality) {
    ItemQuality.POOR -> Color(0xFF9D9D9D)
    ItemQuality.COMMON, null -> Color(0xFFE8E0D0)
    ItemQuality.UNCOMMON -> Color(0xFF1EFF00)
    ItemQuality.RARE -> Color(0xFF0070DD)
    ItemQuality.EPIC -> Color(0xFFA335EE)
    ItemQuality.LEGENDARY -> Color(0xFFFF8000)
    ItemQuality.ARTIFACT -> Color(0xFFE6CC80)
    ItemQuality.HEIRLOOM -> Color(0xFF00CCFF)
}

@Composable
fun AuctionsScreen(viewModel: AuctionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Screen {
        item {
            ScreenTitle(
                stringResource(R.string.title_auctions),
                subtitle = stringResource(R.string.auctions_subtitle),
            )
        }

        item {
            SegmentedRow(
                options = listOf(
                    stringResource(R.string.auctions_scope_commodities),
                    stringResource(R.string.auctions_scope_realm),
                ),
                selected = if (state.scope == AuctionScope.COMMODITIES) 0 else 1,
                onSelect = {
                    viewModel.selectScope(
                        if (it == 0) AuctionScope.COMMODITIES else AuctionScope.REALM,
                    )
                },
            )
        }

        item { FreshnessPanel(state, onRefresh = viewModel::refresh) }

        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.auctions_search_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.md),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }

        item {
            SegmentedRow(
                options = listOf(
                    stringResource(R.string.auctions_tab_expensive),
                    stringResource(R.string.auctions_tab_traded),
                ),
                selected = if (state.tab == AuctionTab.TRADED) 1 else 0,
                onSelect = {
                    viewModel.selectTab(if (it == 0) AuctionTab.EXPENSIVE else AuctionTab.TRADED)
                },
            )
        }

        if (state.loading && state.rows.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(Spacing.xxl), Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        } else if (state.rows.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.auctions_empty),
                    detail = stringResource(R.string.auctions_empty_detail),
                )
            }
        } else {
            items(state.rows.size) { index ->
                val row = state.rows[index]
                Column {
                    AuctionRow(row)
                    if (index < state.rows.lastIndex) Divider()
                }
            }
        }
    }
}

@Composable
private fun AuctionRow(row: AuctionListing) {
    ListRow(
        title = row.name,
        subtitle = stringResource(R.string.auctions_row_stock, row.quantity, row.listings),
        trailing = formatGold(row.minUnitPrice),
        accent = qualityColor(row.quality),
    )
}

/**
 * Estado de la caché. Los volcados de Blizzard pesan más de 20 MB y se
 * regeneran cada hora, así que el usuario tiene que poder ver de cuándo son sus
 * datos y decidir él cuándo gastar esos megas: descargarlos por sorpresa en
 * datos móviles sería una falta de respeto.
 */
@Composable
private fun FreshnessPanel(state: AuctionsUiState, onRefresh: () -> Unit) {
    val status = state.status
    Panel(
        tone = if (status?.stale != false) PanelTone.Raised else PanelTone.Default,
        padding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        status?.updatedAt == null -> stringResource(R.string.auctions_never_loaded)
                        else -> stringResource(
                            R.string.auctions_updated_ago,
                            Duration.between(status.updatedAt, Instant.now()).toMinutes(),
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (status != null && status.items > 0) {
                    Text(
                        stringResource(R.string.auctions_item_count, status.items),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.auctions_download_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            if (state.refreshing) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Pill(
                    stringResource(R.string.auctions_refresh),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onRefresh),
                )
            }
        }
        if (state.error != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Selector de dos o tres opciones. Más barato de leer que unas pestañas. */
@Composable
fun SegmentedRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surface)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(
                        if (active) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
