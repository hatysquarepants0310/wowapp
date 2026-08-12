package com.azeroth.companion.feature.storylines

import androidx.compose.foundation.layout.PaddingValues
import com.azeroth.companion.ui.components.Panel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.res.stringResource
import com.azeroth.companion.ui.components.WowTextField
import com.azeroth.companion.ui.components.WowTextButton
import com.azeroth.companion.ui.components.WowProgress
import com.azeroth.companion.ui.components.WowLoading
import com.azeroth.companion.R
import com.azeroth.companion.data.CategoryKind
import com.azeroth.companion.data.CategoryNode
import com.azeroth.companion.data.SeasonNode
import com.azeroth.companion.data.StorylineProgress
import com.azeroth.companion.data.StorylineQuest
import com.azeroth.companion.data.StorylinesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nivel de la jerarquía que se está viendo (estilo BtWQuests). */
private enum class Level { SEASONS, CATEGORIES, QUESTS }

data class StorylinesState(
    val loading: Boolean = true,
    val hasAccount: Boolean = false,
    val seasons: List<SeasonNode> = emptyList(),
    val season: SeasonNode? = null,
    val categories: List<CategoryNode> = emptyList(),
    val categoriesLoading: Boolean = false,
    val story: StorylineProgress? = null,
    val quests: List<StorylineQuest> = emptyList(),
    val questsLoading: Boolean = false,
    val expandedQuest: Int? = null,
    val query: String = "",
)

@HiltViewModel
class StorylinesViewModel @Inject constructor(
    private val repository: StorylinesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(StorylinesState())
    val state: StateFlow<StorylinesState> = _state

    init {
        viewModelScope.launch {
            val (hasAccount, seasons) = repository.seasons()
            _state.value = _state.value.copy(
                loading = false, hasAccount = hasAccount, seasons = seasons,
            )
        }
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(query = q) }

    fun openSeason(season: SeasonNode) {
        _state.value = _state.value.copy(season = season, categoriesLoading = true, query = "")
        viewModelScope.launch {
            _state.value = _state.value.copy(
                categories = repository.categories(season.expansionId), categoriesLoading = false,
            )
        }
    }

    fun openStory(story: StorylineProgress) {
        _state.value = _state.value.copy(story = story, quests = emptyList(), questsLoading = true)
        viewModelScope.launch {
            _state.value = _state.value.copy(
                quests = repository.storylineQuests(story.id), questsLoading = false,
            )
        }
    }

    fun toggleQuest(id: Int) {
        val opening = _state.value.expandedQuest != id
        _state.value = _state.value.copy(expandedQuest = if (opening) id else null)
        // La lista sale de los assets (instantánea y sin conexión); la
        // descripción y la recompensa se piden solo al abrir el detalle.
        if (!opening) return
        val quest = _state.value.quests.firstOrNull { it.id == id } ?: return
        if (quest.description != null || quest.rewardItems.isNotEmpty()) return
        viewModelScope.launch {
            val detail = repository.questDetail(id)
            if (detail.description == null && detail.rewards.isEmpty()) return@launch
            _state.value = _state.value.copy(
                quests = _state.value.quests.map {
                    if (it.id == id) {
                        it.copy(description = detail.description, rewardItems = detail.rewards)
                    } else it
                },
            )
        }
    }

    /** Sube un nivel; devuelve false si ya estamos en la raíz. */
    fun goBack(): Boolean = when {
        _state.value.story != null -> {
            _state.value = _state.value.copy(story = null, quests = emptyList(), expandedQuest = null)
            true
        }
        _state.value.season != null -> {
            _state.value = _state.value.copy(season = null, categories = emptyList(), query = "")
            true
        }
        else -> false
    }

    /** Título de la barra según el nivel actual. */
    val title: String
        get() = _state.value.story?.name
            ?: _state.value.season?.name
            ?: "Historias"
}

@Composable
fun StorylinesScreen(
    onOpenQuest: (Int) -> Unit = {},
    viewModel: StorylinesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // El botón atrás sube un nivel de la jerarquía antes de salir de la pantalla.
    androidx.activity.compose.BackHandler(
        enabled = state.story != null || state.season != null,
    ) { viewModel.goBack() }

    when {
        state.story != null -> QuestList(state, viewModel, onOpenQuest)
        state.season != null -> CategoryList(state, viewModel)
        state.loading -> Center { WowLoading() }
        else -> SeasonList(state, viewModel)
    }
}

// ---------- Nivel 1: temporadas ----------

/** Color distintivo por expansión, para que cada temporada se reconozca de un vistazo. */
private fun expansionColors(id: Int): Pair<Color, Color> = when (id) {
    11 -> Color(0xFF9B7BFF) to Color(0xFF3B2A66)   // Midnight
    10 -> Color(0xFF4FB6A6) to Color(0xFF14403A)   // The War Within
    9 -> Color(0xFFE0864F) to Color(0xFF4A2A17)    // Dragonflight
    8 -> Color(0xFF8FA8C8) to Color(0xFF23303F)    // Shadowlands
    7 -> Color(0xFFD9A441) to Color(0xFF4A380F)    // BfA
    6 -> Color(0xFF77D169) to Color(0xFF1F3D1B)    // Legion
    5 -> Color(0xFFC1663F) to Color(0xFF3D2013)    // WoD
    4 -> Color(0xFF6FC28C) to Color(0xFF1B3A28)    // MoP
    3 -> Color(0xFFE05A4B) to Color(0xFF441915)    // Cataclysm
    2 -> Color(0xFF9FD8E8) to Color(0xFF1E3740)    // WotLK
    1 -> Color(0xFF9BD14F) to Color(0xFF2C3D14)    // TBC
    else -> Color(0xFFB8A98F) to Color(0xFF322A1E)  // Classic
}

@Composable
private fun SeasonList(state: StorylinesState, viewModel: StorylinesViewModel) {
    val filtered = state.seasons.filter {
        state.query.isBlank() || it.name.contains(state.query, ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        WowTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = stringResource(R.string.storylines_search_season),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!state.hasAccount) {
            Text(stringResource(R.string.storylines_need_sync),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp))
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            items(filtered, key = { it.expansionId }) { season ->
                SeasonCard(season) { viewModel.openSeason(season) }
            }
        }
    }
}

@Composable
private fun SeasonCard(season: SeasonNode, onClick: () -> Unit) {
    val (accent, deep) = expansionColors(season.expansionId)
    Box(
        Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(deep, MaterialTheme.colorScheme.surface)))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Emblema de la temporada: inicial sobre disco del color de la expansión.
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        season.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(season.name, style = MaterialTheme.typography.titleMedium,
                            color = accent, fontWeight = FontWeight.SemiBold)
                        if (season.isCurrent) {
                            Text(stringResource(R.string.storylines_current), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        buildString {
                            append(stringResource(R.string.storylines_progress, season.completed, season.total))
                            if (season.campaignCount > 0) {
                                append(" · " + stringResource(R.string.storylines_campaigns, season.campaignCount))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (season.total > 0) {
                        WowProgress(
                            season.completed.toFloat() / season.total,
                            color = accent,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                }
                Text("›", style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------- Nivel 2: categorías e historias numeradas ----------

@Composable
private fun CategoryList(state: StorylinesState, viewModel: StorylinesViewModel) {
    if (state.categoriesLoading) { Center { WowLoading() }; return }
    val accent = expansionColors(state.season?.expansionId ?: 0).first
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        WowTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = stringResource(R.string.storylines_search),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (state.categories.isEmpty()) {
            Text(stringResource(R.string.storylines_empty_season),
                Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            state.categories.forEach { node ->
                val visible = node.storylines.filter {
                    state.query.isBlank() || it.name.contains(state.query, true) ||
                        (it.zone?.contains(state.query, true) == true)
                }
                if (visible.isEmpty()) return@forEach
                item(key = "cat_${node.key}") {
                    CategoryHeader(node, accent)
                }
                items(visible, key = { "st_${it.id}" }) { story ->
                    StoryRow(story, accent) { viewModel.openStory(story) }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/**
 * Cabecera de categoría: el nombre REAL de la campaña del juego (o la zona),
 * con cuántas de sus historias están completas.
 */
@Composable
private fun CategoryHeader(node: CategoryNode, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (node.kind == CategoryKind.CAMPAIGN) "⚔" else "🗺",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                node.label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "${node.completed}/${node.total}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StoryRow(story: StorylineProgress, accent: Color, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().clickable { onClick() }, padding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Número de orden sugerido dentro de la categoría.
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(if (story.done) accent.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (story.done) "✓" else story.order.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (story.done) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(story.name, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    story.zone?.let {
                        Text("📍 $it", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${story.completed}/${story.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (story.done) accent
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (story.completed > 0 && !story.done) {
                    WowProgress(
                        story.completed.toFloat() / story.total,
                        color = accent,
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    )
                }
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- Nivel 3: misiones de la historia ----------

@Composable
private fun QuestList(
    state: StorylinesState,
    viewModel: StorylinesViewModel,
    onOpenQuest: (Int) -> Unit,
) {
    val story = state.story!!
    val accent = expansionColors(state.season?.expansionId ?: 0).first
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.storylines_required, story.completed, story.total),
            style = MaterialTheme.typography.titleMedium, color = accent,
            modifier = Modifier.padding(top = 10.dp))
        story.campaign?.let {
            Text("⚔ $it", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        story.zone?.let {
            Text("📍 $it", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (story.optionalTotal > 0) {
            Text(
                stringResource(R.string.storylines_optional, story.optionalCompleted, story.optionalTotal),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.questsLoading) { Center { WowLoading() }; return }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            items(state.quests, key = { it.id }) { q ->
                QuestCard(
                    q, accent, state.expandedQuest == q.id,
                    onClick = { viewModel.toggleQuest(q.id) },
                    onOpenDetail = { onOpenQuest(q.id) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun QuestCard(
    q: StorylineQuest,
    accent: Color,
    expanded: Boolean,
    onClick: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    Panel(Modifier.fillMaxWidth().clickable { onClick() }, padding = PaddingValues(0.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(28.dp)
                        .background(if (q.completed) accent.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (q.completed) "✓" else if (q.optional) "·" else q.step.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (q.completed) accent
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(q.name, style = MaterialTheme.typography.bodyMedium,
                        color = if (q.completed) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (q.optional) {
                        Text(stringResource(R.string.storylines_optional_tag), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Text(if (expanded) "▾" else "▸",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 38.dp, top = 6.dp)) {
                    q.zone?.let { Detail("Zona", it) }
                    if (q.minLevel > 0) Detail("Nivel mínimo", q.minLevel.toString())
                    WowTextButton(
                        stringResource(R.string.storylines_open_detail),
                        onClick = onOpenDetail,
                    )
                    if (q.rewardItems.isNotEmpty()) {
                        Text(
                            if (q.rewardItems.size > 1) "Recompensa (eliges una)" else "Recompensa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        q.rewardItems.forEach { reward -> QuestRewardRow(reward) }
                    }
                    q.description?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

/** Recompensa de misión con su imagen: la misma lectura visual que el equipo. */
@Composable
private fun QuestRewardRow(reward: com.azeroth.companion.data.QuestReward) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (reward.iconUrl != null) {
                coil.compose.AsyncImage(
                    model = reward.iconUrl,
                    contentDescription = reward.name,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Text("🎁", style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(reward.name, style = MaterialTheme.typography.bodySmall)
            Text(
                reward.chanceExplanation,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label:", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) { content() }
}
