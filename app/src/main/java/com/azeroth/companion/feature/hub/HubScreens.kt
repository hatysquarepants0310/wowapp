package com.azeroth.companion.feature.hub

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.azeroth.companion.R
import com.azeroth.companion.feature.content.ContentScreen
import com.azeroth.companion.feature.live.LiveMapScreen
import com.azeroth.companion.ui.components.SectionHeader
import com.azeroth.companion.ui.components.Spacing
import com.azeroth.companion.ui.components.WowChip

/**
 * Mundo es el mapa, no un índice. Noticias y eventos cuelgan de una fila
 * de placas: si al abrir la pestaña ves tarjetas de menú, la app te está
 * haciendo navegar en vez de enseñarte Azeroth.
 */
@Composable
fun WorldHubScreen(
    onNavigate: (String) -> Unit,
    onOpenQuest: (Int) -> Unit,
) {
    LiveMapScreen(
        onOpenQuest = onOpenQuest,
        header = { HubChipRow(worldLinks(onNavigate)) },
    )
}

/**
 * Personaje: la pestaña ES la pantalla del personaje. Los enlaces hermanos
 * van al final, donde estorban menos.
 */
@Composable
fun CharacterHubScreen(onNavigate: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        com.azeroth.companion.feature.character.CharacterScreen(
            footer = {
                SectionHeader(stringResource(R.string.hub_character_more))
                CharacterLinks(onNavigate)
            },
        )
    }
}

@Composable
private fun CharacterLinks(onNavigate: (String) -> Unit) {
    HubChipRow(
        listOf(
            stringResource(R.string.title_score) to { onNavigate("score") },
            stringResource(R.string.nav_roster) to { onNavigate("roster") },
            stringResource(R.string.nav_progression) to { onNavigate("progression") },
            stringResource(R.string.title_seasons_mplus) to { onNavigate("seasons") },
        ),
    )
}

/**
 * Contenido es la enciclopedia, no un menú de la enciclopedia. Historias,
 * misiones y botín de temporada se alcanzan desde la misma fila de placas.
 */
@Composable
fun ContentHubScreen(onNavigate: (String) -> Unit) {
    ContentScreen(
        header = { HubChipRow(contentLinks(onNavigate)) },
    )
}

@Composable
private fun worldLinks(onNavigate: (String) -> Unit) = listOf(
    stringResource(R.string.title_news) to { onNavigate("news") },
    stringResource(R.string.nav_events) to { onNavigate("events") },
)

@Composable
private fun contentLinks(onNavigate: (String) -> Unit) = listOf(
    stringResource(R.string.title_storylines) to { onNavigate("storylines") },
    stringResource(R.string.title_quests_zone) to { onNavigate("quests") },
    stringResource(R.string.title_season_loot) to { onNavigate("seasonloot") },
    stringResource(R.string.title_season_rewards) to { onNavigate("seasonal") },
)

@Composable
private fun HubChipRow(entries: List<Pair<String, () -> Unit>>) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        entries.forEach { (label, onClick) ->
            WowChip(label, selected = false, onClick = onClick)
        }
    }
}
