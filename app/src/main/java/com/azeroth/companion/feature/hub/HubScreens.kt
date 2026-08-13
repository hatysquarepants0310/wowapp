package com.azeroth.companion.feature.hub

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import com.azeroth.companion.ui.components.Spacing
import com.azeroth.companion.ui.components.WowChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.azeroth.companion.R
import com.azeroth.companion.ui.components.Screen
import com.azeroth.companion.ui.components.ScreenTitle
import com.azeroth.companion.ui.components.SectionHeader

/**
 * Hub de MUNDO: lo que pasa fuera de tu personaje.
 *
 * El mapa en vivo es lo que la gente viene a ver, así que va arriba y en
 * grande; los eventos con cadencia y las noticias son el contexto.
 */
@Composable
fun WorldHubScreen(onNavigate: (String) -> Unit) {
    val entries = listOf(
                HubEntry(
                    "news", stringResource(R.string.title_news),
                    stringResource(R.string.more_news_desc), Icons.Filled.Newspaper,
                ),
                HubEntry(
                    "events", stringResource(R.string.nav_events),
                    stringResource(R.string.more_events_desc), Icons.Filled.Timer,
                ),
            )
    Screen {
        item {
            ScreenTitle(
                stringResource(R.string.tab_world),
                subtitle = stringResource(R.string.hub_world_subtitle),
            )
        }
        item {
            HubFeature(
                title = stringResource(R.string.title_live),
                subtitle = stringResource(R.string.more_live_desc),
                icon = Icons.Filled.Map,
                onClick = { onNavigate("live") },
            )
        }
        item { SectionHeader(stringResource(R.string.hub_world_more)) }
        hubGrid(entries, onNavigate)
    }
}

/**
 * Hub de PERSONAJE: todo lo que es tuyo.
 *
 * Antes esto estaba repartido entre la pestaña Personaje y el cajón de "Más":
 * la puntuación, el roster y la progresión vivían en sitios distintos aunque
 * hablen de lo mismo.
 */
@Composable
fun CharacterHubScreen(onNavigate: (String) -> Unit) {
    // Esta pestaña ERA un menú de cuatro tarjetas que llevaban a otros menús.
    //
    // Eso es exactamente el defecto que la app tenía que evitar: si al abrir
    // "Personaje" lo que ves es un índice, la app no te está enseñando nada
    // sobre tu personaje, te está haciendo navegar para llegar a lo que ya
    // querías ver. Es ser una enciclopedia peor que la enciclopedia.
    //
    // Ahora la pestaña ES la pantalla del personaje —tu equipo pieza a pieza,
    // con los iconos y los colores de calidad— y los enlaces a las secciones
    // hermanas van en una fila compacta al final, que es donde estorban menos.
    Box(Modifier.fillMaxSize()) {
        com.azeroth.companion.feature.character.CharacterScreen(
            footer = {
                SectionHeader(stringResource(R.string.hub_character_more))
                CharacterLinks(onNavigate)
            },
        )
    }
}

/** Los accesos hermanos, en una sola fila de placas en vez de cuatro tarjetas. */
@Composable
private fun CharacterLinks(onNavigate: (String) -> Unit) {
    val entries = listOf(
        "score" to stringResource(R.string.title_score),
        "roster" to stringResource(R.string.nav_roster),
        "progression" to stringResource(R.string.nav_progression),
        "seasons" to stringResource(R.string.title_seasons_mplus),
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        entries.forEach { (route, label) ->
            WowChip(label, selected = false, onClick = { onNavigate(route) })
        }
    }
}

/**
 * Hub de CONTENIDO: la enciclopedia. Lo que existe en el juego, no lo que has
 * hecho tú.
 */
@Composable
fun ContentHubScreen(onNavigate: (String) -> Unit) {
    val entries = listOf(
                HubEntry(
                    "storylines", stringResource(R.string.title_storylines),
                    stringResource(R.string.more_storylines_desc), Icons.Filled.AutoStories,
                ),
                HubEntry(
                    "quests", stringResource(R.string.title_quests_zone),
                    stringResource(R.string.more_quests_desc), Icons.Filled.Explore,
                ),
                HubEntry(
                    "seasonloot", stringResource(R.string.title_season_loot),
                    stringResource(R.string.more_seasonloot_desc), Icons.Filled.Diamond,
                ),
                HubEntry(
                    "seasonal", stringResource(R.string.title_season_rewards),
                    stringResource(R.string.more_seasonal_desc), Icons.Filled.CalendarMonth,
                ),
            )
    Screen {
        item {
            ScreenTitle(
                stringResource(R.string.tab_content),
                subtitle = stringResource(R.string.hub_content_subtitle),
            )
        }
        item {
            HubFeature(
                title = stringResource(R.string.title_content),
                subtitle = stringResource(R.string.more_content_desc),
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = { onNavigate("content") },
            )
        }
        item { SectionHeader(stringResource(R.string.hub_content_more)) }
        hubGrid(entries, onNavigate)
    }
}
