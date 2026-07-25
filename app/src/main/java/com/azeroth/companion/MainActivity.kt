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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleOAuthRedirect(intent)
        // Automático primero: cada apertura refresca los datos de la cuenta
        // sin que el usuario tenga que pedirlo (trabajo único, no se acumula).
        SyncScheduler.syncNow(this)
        // Al tocar una notificación de evento se abre directo su checklist (§5.4).
        val eventIdFromNotification = intent?.getStringExtra(AlarmReceiver.EXTRA_EVENT_ID)
        setContent {
            AzerothTheme {
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

@Composable
private fun AppScaffold(startEventId: String?) {
    val navController: NavHostController = rememberNavController()
    val destinations = listOf(
        Destination("dashboard", R.string.nav_dashboard, Icons.Filled.Home),
        Destination("character", R.string.nav_character, Icons.Filled.Person),
        Destination("more", R.string.nav_more, Icons.Filled.Menu),
    )
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo("dashboard") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
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
                )
            }
            composable("events") {
                EventsScreen(onOpenDetail = { navController.navigate("event/$it") })
            }
            composable("event/{eventId}") { entry ->
                EventDetailScreen(eventId = entry.arguments?.getString("eventId").orEmpty())
            }
            composable("weekly") { WeeklyScreen() }
            composable("content") { com.azeroth.companion.feature.content.ContentScreen() }
            composable("character") { com.azeroth.companion.feature.character.CharacterScreen() }
            composable("seasons") { com.azeroth.companion.feature.seasons.SeasonsScreen() }
            composable("quests") { com.azeroth.companion.feature.quests.QuestTrackerScreen() }
            composable("storylines") { com.azeroth.companion.feature.storylines.StorylinesScreen() }
            composable("progression") { ProgressionScreen() }
            composable("seasonal") { SeasonalScreen() }
            composable("roster") { RosterScreen() }
            composable("settings") { SettingsScreen() }
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
    val purple = androidx.compose.ui.graphics.Color(0xFF9B7BFF)
    val gold = androidx.compose.ui.graphics.Color(0xFFD9A441)
    val teal = androidx.compose.ui.graphics.Color(0xFF4FB6A6)
    val rose = androidx.compose.ui.graphics.Color(0xFFE07A9B)
    val items = listOf(
        MoreItem("content", "Contenido", "Mazmorras, bandas, jefes y afijos de M+",
            Icons.AutoMirrored.Filled.MenuBook, purple),
        MoreItem("storylines", "Historias", "Todas las storylines con tu progreso ✓, estilo Wowhead",
            Icons.Filled.AutoStories, rose),
        MoreItem("quests", "Misiones por zona", "Cada zona con tus misiones completadas automáticamente",
            Icons.Filled.Explore, teal),
        MoreItem("events", "Eventos", "Temporizadores, cadencia y recompensas de cada evento",
            Icons.Filled.Timer, gold),
        MoreItem("weekly", "Semanal", "Tus pendientes de la semana, detectados automáticamente",
            Icons.Filled.Checklist, teal),
        MoreItem("progression", "Progresión", "Gran Bóveda, Folio, Presas y Delves",
            Icons.Filled.TrendingUp, purple),
        MoreItem("seasons", "Temporadas M+", "Tu historial y progreso por temporada de Mythic+",
            Icons.Filled.EmojiEvents, gold),
        MoreItem("seasonal", "Recompensas de temporada", "Objetivos con fecha límite y viabilidad",
            Icons.Filled.CalendarMonth, rose),
        MoreItem("roster", "Roster", "Todos tus personajes y sus intentos de montura",
            Icons.Filled.Groups, teal),
        MoreItem("settings", "Ajustes", "Cuenta, región, actualización, datos y diagnóstico",
            Icons.Filled.Settings, gold),
    )
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        items(items) { item ->
            Card(Modifier.fillMaxWidth().clickable { onNavigate(item.route) }) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(46.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(item.tint.copy(alpha = 0.18f)),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = item.tint)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
