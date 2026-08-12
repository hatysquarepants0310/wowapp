package com.azeroth.companion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.azeroth.companion.core.notifications.AlarmReceiver
import com.azeroth.companion.ui.components.NavItem
import com.azeroth.companion.ui.components.WowIconButton
import com.azeroth.companion.ui.components.WowNavBar
import com.azeroth.companion.ui.components.WowTopBar
import com.azeroth.companion.feature.dashboard.DashboardScreen
import com.azeroth.companion.feature.events.EventDetailScreen
import com.azeroth.companion.feature.events.EventsScreen
import com.azeroth.companion.feature.progression.ProgressionScreen
import com.azeroth.companion.feature.roster.RosterScreen
import com.azeroth.companion.feature.seasonal.SeasonalScreen
import com.azeroth.companion.feature.settings.SettingsScreen
import com.azeroth.companion.feature.weekly.WeeklyScreen
import com.azeroth.companion.sync.SyncScheduler
import com.azeroth.companion.ui.theme.AzerothTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject
    lateinit var authManager: com.azeroth.companion.core.network.AuthManager

    @javax.inject.Inject
    lateinit var settingsRepository: com.azeroth.companion.core.datastore.SettingsRepository

    /**
     * Aplica el idioma elegido antes de crear la UI. Si no hay preferencia, se
     * respeta el del sistema (autodetección en el primer arranque).
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        val tag = runCatching {
            com.azeroth.companion.core.datastore.LanguagePref.read(newBase)
        }.getOrNull()
        super.attachBaseContext(
            if (tag.isNullOrBlank()) newBase else {
                val locale = java.util.Locale.forLanguageTag(tag)
                java.util.Locale.setDefault(locale)
                val config = android.content.res.Configuration(newBase.resources.configuration)
                config.setLocale(locale)
                newBase.createConfigurationContext(config)
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleOAuthRedirect(intent)
        // Automático primero: cada apertura refresca los datos de la cuenta
        // sin que el usuario tenga que pedirlo (trabajo único, no se acumula).
        SyncScheduler.syncNow(this)
        // Al tocar una notificación de evento se abre directo su checklist (§5.4).
        val eventIdFromNotification = intent?.getStringExtra(AlarmReceiver.EXTRA_EVENT_ID)
        setContent {
            // El tema toma el color de la clase del personaje activo.
            com.azeroth.companion.ui.theme.AzerothThemeForActiveCharacter {
                AppScaffold(startEventId = eventIdFromNotification)
                // Pop-up de actualización al abrir (instalar ahora / después).
                com.azeroth.companion.feature.update.UpdateGate()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthRedirect(intent)
    }

    /** Deep link azerothcompanion://oauth?code=... del flujo PKCE (§2.1). */
    private fun handleOAuthRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "azerothcompanion" || data.host != "oauth") return
        val code = data.getQueryParameter("code") ?: return
        val returnedState = data.getQueryParameter("state")
        lifecycleScope.launch {
            val region = settingsRepository.settings.first().region
            authManager.handleRedirect(code, returnedState, region)
            SyncScheduler.syncNow(this@MainActivity)
        }
    }
}

private data class Destination(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/** Títulos de las subpantallas para la barra superior con botón atrás. */
private val subScreenTitles = mapOf(
    "content" to R.string.title_content,
    "storylines" to R.string.title_storylines,
    "quests" to R.string.title_quests_zone,
    "events" to R.string.nav_events,
    "event" to R.string.title_event,
    "weekly" to R.string.title_this_week,
    "progression" to R.string.nav_progression,
    "seasons" to R.string.title_seasons_mplus,
    "seasonal" to R.string.title_season_rewards,
    "seasonloot" to R.string.title_season_loot,
    "quest" to R.string.title_quest,
    "roster" to R.string.nav_roster,
    "settings" to R.string.nav_settings,
    "auctions" to R.string.title_auctions,
    "score" to R.string.title_score,
    "news" to R.string.title_news,
    "article" to R.string.title_news,
    "live" to R.string.title_live,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(startEventId: String?) {
    val navController: NavHostController = rememberNavController()
    // Arquitectura de la app.
    //
    // Antes eran tres pestañas —Inicio, Personaje y Más— y "Más" era un cajón
    // de sastre con doce entradas planas: todo lo que no cabía en las otras dos
    // acababa ahí. Ahora cada pestaña tiene un tema claro y no hay cajón:
    //
    //   Hoy       lo que caduca: eventos, reset y misiones de la semana
    //   Personaje lo tuyo: equipo, puntuación, roster, progresión
    //   Mundo     lo de fuera: mapa en vivo, eventos, noticias
    //   Contenido la enciclopedia: mazmorras, bandas, botín, historias
    //   Mercado   la casa de subastas
    //
    // Ajustes sale de la lista y pasa al engranaje de la barra superior, que es
    // donde se busca en cualquier app.
    val destinations = listOf(
        Destination("dashboard", R.string.tab_today, Icons.Filled.Today),
        Destination("characterHub", R.string.tab_character, Icons.Filled.Person),
        Destination("worldHub", R.string.tab_world, Icons.Filled.Public),
        Destination("contentHub", R.string.tab_content, Icons.AutoMirrored.Filled.MenuBook),
        Destination("market", R.string.tab_market, Icons.Filled.Storefront),
    )
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val rootRoutes = destinations.map { it.route }.toSet()
    // Las subpantallas de "Más" necesitan salida visible: sin esto parecía que
    // te quedabas atrapado hasta usar el botón atrás del sistema.
    val isSubScreen = currentRoute != null && currentRoute !in rootRoutes
    val subTitleRes = subScreenTitles[currentRoute?.substringBefore('/')]

    // El chrome ya no es de Material: `TopAppBar` y `NavigationBar` traen sus
    // alturas, su tipografía y —la barra inferior— la píldora que crece detrás
    // del icono activo, que es la seña visual de Android más reconocible que
    // existe. `WowTopBar` y `WowNavBar` dibujan chapa biselada y marcan lo activo
    // con un filo de acento, que es como lo marca el juego.
    Scaffold(
        topBar = {
            if (isSubScreen) {
                WowTopBar(
                    title = subTitleRes?.let { stringResource(it) } ?: "",
                    navigation = {
                        WowIconButton(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            onClick = { navController.popBackStack() },
                        )
                    },
                )
            } else {
                // Ajustes sale del menú y pasa al engranaje: es donde se busca
                // en cualquier app, y así la barra inferior queda solo para
                // navegar por contenido.
                WowTopBar(
                    title = stringResource(R.string.app_name),
                    actions = {
                        WowIconButton(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.nav_settings_short),
                            onClick = { navController.navigate("settings") },
                        )
                    },
                )
            }
        },
        bottomBar = {
            WowNavBar(
                items = destinations.map {
                    NavItem(it.route, stringResource(it.labelRes), it.icon)
                },
                selectedRoute = currentRoute,
                onSelect = { route ->
                    navController.navigate(route) {
                        popUpTo("dashboard") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { padding ->
        androidx.compose.runtime.LaunchedEffect(startEventId) {
            if (startEventId != null) navController.navigate("event/$startEventId")
        }
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding),
        ) {
            composable("dashboard") {
                DashboardScreen(
                    onOpenChecklist = { navController.navigate("event/$it") },
                    onOpenRoster = { navController.navigate("roster") },
                    onOpenSeasonLoot = { navController.navigate("seasonloot") },
                    onOpenWeekly = { navController.navigate("weekly") },
                )
            }
            composable("events") {
                EventsScreen(onOpenDetail = { navController.navigate("event/$it") })
            }
            composable("event/{eventId}") { entry ->
                EventDetailScreen(eventId = entry.arguments?.getString("eventId").orEmpty())
            }
            composable("weekly") {
                WeeklyScreen(
                    onOpenSource = { instanceId, bossId ->
                        navController.navigate("content/$instanceId/$bossId")
                    },
                    onOpenQuest = { navController.navigate("quest/$it") },
                )
            }
            composable(
                "quest/{questId}",
                arguments = listOf(
                    androidx.navigation.navArgument("questId") {
                        type = androidx.navigation.NavType.IntType
                    },
                ),
            ) { entry ->
                com.azeroth.companion.feature.quest.QuestDetailScreen(
                    questId = entry.arguments?.getInt("questId") ?: 0,
                )
            }
            composable("seasonloot") {
                com.azeroth.companion.feature.loot.SeasonLootScreen(
                    onOpenSource = { instanceId, bossId ->
                        navController.navigate("content/$instanceId/$bossId")
                    },
                )
            }
            composable("content") { com.azeroth.companion.feature.content.ContentScreen() }
            composable(
                "content/{instanceId}/{bossId}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") {
                        type = androidx.navigation.NavType.IntType
                    },
                    androidx.navigation.navArgument("bossId") {
                        type = androidx.navigation.NavType.IntType
                    },
                ),
            ) { entry ->
                com.azeroth.companion.feature.content.ContentScreen(
                    focusInstanceId = entry.arguments?.getInt("instanceId") ?: 0,
                    focusBossId = entry.arguments?.getInt("bossId") ?: 0,
                )
            }
            composable("character") { com.azeroth.companion.feature.character.CharacterScreen() }
            composable("seasons") { com.azeroth.companion.feature.seasons.SeasonsScreen() }
            composable("quests") { com.azeroth.companion.feature.quests.QuestTrackerScreen() }
            composable("storylines") {
                com.azeroth.companion.feature.storylines.StorylinesScreen(
                    onOpenQuest = { navController.navigate("quest/$it") },
                )
            }
            composable("progression") { ProgressionScreen() }
            composable("seasonal") { SeasonalScreen() }
            composable("roster") { RosterScreen() }
            composable("settings") { SettingsScreen() }
            composable("characterHub") {
                com.azeroth.companion.feature.hub.CharacterHubScreen(
                    onNavigate = { navController.navigate(it) },
                )
            }
            composable("worldHub") {
                com.azeroth.companion.feature.hub.WorldHubScreen(
                    onNavigate = { navController.navigate(it) },
                )
            }
            composable("contentHub") {
                com.azeroth.companion.feature.hub.ContentHubScreen(
                    onNavigate = { navController.navigate(it) },
                )
            }
            composable("market") { com.azeroth.companion.feature.auctions.AuctionsScreen() }
            composable("score") { com.azeroth.companion.feature.score.ScoreScreen() }
            composable("auctions") { com.azeroth.companion.feature.auctions.AuctionsScreen() }
            composable("news") {
                com.azeroth.companion.feature.news.NewsScreen(
                    onOpenArticle = { navController.navigate("article/$it") },
                )
            }
            composable("article/{articleId}") { entry ->
                com.azeroth.companion.feature.news.ArticleScreen(
                    articleId = entry.arguments?.getString("articleId").orEmpty(),
                )
            }
            composable("live") {
                com.azeroth.companion.feature.live.LiveMapScreen(
                    onOpenQuest = { navController.navigate("quest/$it") },
                )
            }
            composable("more") {
                MoreScreen(onNavigate = { route -> navController.navigate(route) })
            }
        }
    }
}

private data class MoreItem(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: androidx.compose.ui.graphics.Color,
)

@Composable
private fun MoreScreen(onNavigate: (String) -> Unit) {
    val purple = androidx.compose.ui.graphics.Color(0xFFA98BFF)
    val gold = androidx.compose.ui.graphics.Color(0xFFE0B457)
    val teal = androidx.compose.ui.graphics.Color(0xFF6FC7C0)
    val rose = androidx.compose.ui.graphics.Color(0xFFE08AA6)

    // Agrupado por para qué sirve cada cosa. Once entradas seguidas en una
    // lista plana obligaban a leerlas todas para encontrar una; con tres grupos
    // cortos se llega por descarte.
    val groups = listOf(
        stringResource(R.string.more_group_world) to listOf(
            MoreItem("live", stringResource(R.string.title_live), stringResource(R.string.more_live_desc),
                Icons.Filled.Explore, teal),
            MoreItem("news", stringResource(R.string.title_news), stringResource(R.string.more_news_desc),
                Icons.AutoMirrored.Filled.MenuBook, rose),
            MoreItem("events", stringResource(R.string.nav_events), stringResource(R.string.more_events_desc),
                Icons.Filled.Timer, gold),
            MoreItem("auctions", stringResource(R.string.title_auctions), stringResource(R.string.more_auctions_desc),
                Icons.Filled.Diamond, gold),
        ),
        stringResource(R.string.more_group_progress) to listOf(
            MoreItem("weekly", stringResource(R.string.title_this_week), stringResource(R.string.more_weekly_desc),
                Icons.Filled.Checklist, teal),
            MoreItem("progression", stringResource(R.string.nav_progression), stringResource(R.string.more_progression_desc),
                Icons.Filled.TrendingUp, purple),
            MoreItem("score", stringResource(R.string.title_score), stringResource(R.string.more_score_desc),
                Icons.Filled.TrendingUp, gold),
            MoreItem("seasons", stringResource(R.string.title_seasons_mplus), stringResource(R.string.more_seasons_desc),
                Icons.Filled.EmojiEvents, gold),
            MoreItem("roster", stringResource(R.string.nav_roster), stringResource(R.string.more_roster_desc),
                Icons.Filled.Groups, teal),
        ),
        stringResource(R.string.more_group_content) to listOf(
            MoreItem("content", stringResource(R.string.title_content), stringResource(R.string.more_content_desc),
                Icons.AutoMirrored.Filled.MenuBook, purple),
            MoreItem("storylines", stringResource(R.string.title_storylines), stringResource(R.string.more_storylines_desc),
                Icons.Filled.AutoStories, rose),
            MoreItem("quests", stringResource(R.string.title_quests_zone), stringResource(R.string.more_quests_desc),
                Icons.Filled.Explore, teal),
            MoreItem("seasonloot", stringResource(R.string.title_season_loot), stringResource(R.string.more_seasonloot_desc),
                Icons.Filled.Diamond, purple),
            MoreItem("seasonal", stringResource(R.string.title_season_rewards), stringResource(R.string.more_seasonal_desc),
                Icons.Filled.CalendarMonth, rose),
            MoreItem("settings", stringResource(R.string.nav_settings), stringResource(R.string.more_settings_desc),
                Icons.Filled.Settings, gold),
        ),
    )

    com.azeroth.companion.ui.components.Screen {
        groups.forEach { (title, items) ->
            item { com.azeroth.companion.ui.components.SectionHeader(title) }
            items(items.size) { index ->
                val entry = items[index]
                MoreRow(entry, onNavigate)
                if (index < items.lastIndex) com.azeroth.companion.ui.components.Divider()
            }
        }
    }
}

@Composable
private fun MoreRow(item: MoreItem, onNavigate: (String) -> Unit) {
    com.azeroth.companion.ui.components.ListRow(
        title = item.title,
        subtitle = item.subtitle,
        onClick = { onNavigate(item.route) },
        leading = {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(38.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(item.tint.copy(alpha = 0.16f)),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Icon(item.icon, contentDescription = null, tint = item.tint,
                    modifier = Modifier.size(19.dp))
            }
        },
    )
}
