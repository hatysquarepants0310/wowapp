package com.azeroth.companion.feature.content

import androidx.compose.foundation.layout.width
import com.azeroth.companion.ui.components.GameIcon
import androidx.compose.foundation.layout.PaddingValues
import com.azeroth.companion.ui.components.PanelTone
import com.azeroth.companion.ui.components.Panel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azeroth.companion.R
import com.azeroth.companion.ui.components.WowChip
import com.azeroth.companion.ui.components.WowTabs
import com.azeroth.companion.ui.components.WowTextField
import com.azeroth.companion.ui.components.WowTextButton
import com.azeroth.companion.ui.components.WowLoading
import com.azeroth.companion.ui.components.Spacing
import com.azeroth.companion.data.InstanceSummary

/**
 * Contenido (todo en uno): afijos de M+ de la semana, y mazmorras/bandas de
 * CUALQUIER expansión (la actual destacada, las anteriores aparte para no
 * confundir). Fuentes oficiales — Blizzard Game Data + Raider.IO.
 */
@Composable
fun ContentScreen(
    focusInstanceId: Int = 0,
    focusBossId: Int = 0,
    header: (@Composable () -> Unit)? = null,
    viewModel: ContentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tabs = listOf("Mythic+", "Mazmorras", "Bandas")
    // Al llegar desde un objeto del botín se abre ya en la pestaña correcta.
    var tab by remember {
        mutableIntStateOf(if (focusInstanceId != 0) 2 else 0)
    }
    androidx.compose.runtime.LaunchedEffect(focusInstanceId, focusBossId) {
        viewModel.focusOn(focusInstanceId, focusBossId)
    }
    // La instancia puede ser mazmorra o banda: se decide con el contenido ya cargado.
    androidx.compose.runtime.LaunchedEffect(state.expansion, focusInstanceId) {
        if (focusInstanceId != 0 && state.expansion != null) {
            tab = if (state.expansion?.raids?.any { it.id == focusInstanceId } == true) 2 else 1
        }
    }

    Column(Modifier.fillMaxSize()) {
        header?.let { slot ->
            Box(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
                slot()
            }
        }
        // Barra de búsqueda: filtra mazmorras, bandas y afijos por nombre en cliente.
        // Si el query coincide en otra pestaña, se cambia de pestaña automáticamente
        // para que el jugador encuentre directamente lo que busca, como en Wowhead.
        val query = state.filterQuery
        if (query.isNotBlank()) {
            val matchesAffixes = state.affixes.any { it.matches(query) }
            val matchesDungeons = state.expansion?.dungeons?.any { it.matches(query) } == true
            val matchesRaids = state.expansion?.raids?.any { it.matches(query) } == true
            if (!matchesAffixes && matchesDungeons && tab != 1) tab = 1
            if (!matchesAffixes && matchesRaids && tab != 2) tab = 2
        }
        WowTextField(
            value = query,
            onValueChange = viewModel::setFilterQuery,
            placeholder = stringResource(R.string.content_search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        )
        WowTabs(tabs, tab, onSelect = { tab = it })

        state.error?.let {
            Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        }

        when (tab) {
            0 -> AffixesTab(state, viewModel)
            1 -> InstancesTab(isRaid = false, state = state, viewModel = viewModel)
            2 -> InstancesTab(isRaid = true, state = state, viewModel = viewModel)
        }
    }
}

/** Un nombre de afijo o instancia coincide si el query aparece como subcadena. */
private fun Affix.matches(query: String) = name.contains(query, ignoreCase = true)

/** Un nombre de instancia coincide si el query aparece como subcadena. */
private fun InstanceSummary.matches(query: String) = name.contains(query, ignoreCase = true)

@Composable
private fun AffixesTab(state: ContentState, viewModel: ContentViewModel) {
    if (state.loading && state.affixes.isEmpty()) { Loading(); return }
    val query = state.filterQuery
    val affixes = if (query.isBlank()) state.affixes else state.affixes.filter { it.matches(query) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Afijos de la semana", style = MaterialTheme.typography.titleMedium)
            if (state.affixTitle.isNotBlank()) {
                Text(state.affixTitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
        }
        if (affixes.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.content_empty, query),
                    Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(affixes) { affix ->
                // Con el icono delante, la fila se reconoce sin leerla: el jugador
                // ya sabe qué es Tiránica por su icono. Sin él eran cuatro párrafos
                // idénticos de texto.
                Panel(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                    Row(Modifier.padding(12.dp)) {
                        GameIcon(affix.iconUrl, size = 44.dp, contentDescription = affix.name)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(affix.name, style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary)
                            Text(affix.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        // Mazmorras de la temporada actual con acceso a jefes y botín.
        state.expansion?.dungeons?.takeIf { it.isNotEmpty() }?.let { dungeons ->
            item {
                Spacer(Modifier.height(6.dp))
                Text("Mazmorras de la temporada", style = MaterialTheme.typography.titleMedium)
                Text("Toca una mazmorra y luego un jefe para ver su botín.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(dungeons, key = { "mplus_${it.id}" }) { dungeon ->
                InstanceCard(dungeon, state, viewModel)
            }
        }
        item {
            Text("Fuentes: Raider.IO (afijos) · Blizzard Game Data (mazmorras y botín)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun InstancesTab(isRaid: Boolean, state: ContentState, viewModel: ContentViewModel) {
    Column(Modifier.fillMaxSize()) {
        ExpansionSelector(state, viewModel)
        if (state.loading) { Loading(); return }
        val instances = if (isRaid) state.expansion?.raids else state.expansion?.dungeons
        if (instances.isNullOrEmpty()) {
            Text("Sin datos de contenido para esta expansión.",
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        val query = state.filterQuery
        val filtered = if (query.isBlank()) instances else instances.filter { it.matches(query) }
        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.content_empty, query),
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { instance ->
                InstanceCard(instance, state, viewModel)
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ExpansionSelector(state: ContentState, viewModel: ContentViewModel) {
    val current = state.expansions.filter { it.isCurrent }
    val past = state.expansions.filter { !it.isCurrent }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            current.forEach { exp ->
                WowChip(
                    exp.name,
                    selected = state.selectedExpansionId == exp.id,
                    onClick = { viewModel.selectExpansion(exp.id) },
                )
            }
        }
        WowTextButton(
            if (state.showPastExpansions) {
                "Ocultar expansiones anteriores"
            } else {
                "Ver expansiones anteriores (${past.size})"
            },
            onClick = { viewModel.togglePastExpansions() },
        )
        AnimatedVisibility(visible = state.showPastExpansions) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                past.forEach { exp ->
                    if (state.selectedExpansionId == exp.id) {
                        WowChip(exp.name, selected = true, onClick = {})
                    } else {
                        WowChip(
                            exp.name,
                            selected = false,
                            onClick = { viewModel.selectExpansion(exp.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstanceCard(instance: InstanceSummary, state: ContentState, viewModel: ContentViewModel) {
    val bosses = state.bossesByInstance[instance.id]
    val focused = state.focusInstanceId == instance.id
    Panel(
        Modifier.fillMaxWidth().clickable { viewModel.loadBosses(instance.id) },
        tone = if (focused) PanelTone.Accent else PanelTone.Default,
        padding = PaddingValues(0.dp),
    ) {
        // La cabecera de la tarjeta lleva el ARTE de la mazmorra a sangre.
        //
        // Esto es lo que faltaba. Una lista de nombres de mazmorra es una lista;
        // la misma lista con la ilustración de cada una detrás es World of
        // Warcraft. El arte es oficial, del Compendio de Aventuras, servido por
        // la CDN de Blizzard.
        Box(Modifier.fillMaxWidth().height(96.dp)) {
            if (instance.artUrl != null) {
                coil.compose.AsyncImage(
                    model = instance.artUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Velo de abajo hacia arriba: el nombre se apoya en la parte oscura
            // y se lee siempre, sea cual sea la ilustración que haya detrás.
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to androidx.compose.ui.graphics.Color.Transparent,
                        0.45f to com.azeroth.companion.ui.theme.Base.copy(alpha = 0.55f),
                        1f to com.azeroth.companion.ui.theme.Base.copy(alpha = 0.95f),
                    ),
                ),
            )
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    instance.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = com.azeroth.companion.ui.theme.TextHigh,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(if (bosses == null) "▸" else "▾",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(12.dp)) {
            AnimatedVisibility(visible = bosses != null) {
                Column(Modifier.padding(top = 6.dp)) {
                    if (bosses.isNullOrEmpty()) {
                        Text("Sin jefes listados o sin conexión.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        bosses.forEachIndexed { i, boss ->
                            BossRow(index = i + 1, boss = boss,
                                loot = state.lootByBoss[boss.id],
                                highlighted = state.focusBossId == boss.id,
                                onClick = { viewModel.loadLoot(boss.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BossRow(
    index: Int,
    boss: com.azeroth.companion.data.Boss,
    loot: List<com.azeroth.companion.data.LootEntry>?,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                "$index. ${boss.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (highlighted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (highlighted) androidx.compose.ui.text.font.FontWeight.Bold else null,
            )
            Text(if (loot == null) "ver botín" else "botín ▾",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(visible = loot != null) {
            Column(Modifier.padding(start = 12.dp, top = 2.dp)) {
                if (loot.isNullOrEmpty()) {
                    Text("Sin botín listado en la API.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    // Imagen, color de calidad y probabilidad estimada: el mismo
                    // componente que usa el botín de temporada.
                    loot.forEach { entry ->
                        com.azeroth.companion.ui.components.LootRow(entry, showSource = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { WowLoading() }
}
