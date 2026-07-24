package com.azeroth.companion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
        Destination("content", R.string.nav_content, Icons.AutoMirrored.Filled.MenuBook),
        Destination("character", R.string.nav_character, Icons.Filled.Person),
        Destination("events", R.string.nav_events, Icons.Filled.Timer),
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

private data class MoreItem(val route: String, val title: String, val subtitle: String)

@Composable
private fun MoreScreen(onNavigate: (String) -> Unit) {
    val items = listOf(
        MoreItem("weekly", "Semanal", "Tus pendientes de la semana, detectados automáticamente"),
        MoreItem("quests", "Misiones por zona", "Cada zona con tus misiones completadas ✓ automáticamente"),
        MoreItem("progression", "Progresión", "Gran Bóveda, Folio, Presas y Delves"),
        MoreItem("seasons", "Temporadas M+", "Tu historial y progreso por temporada de Mythic+"),
        MoreItem("seasonal", "Recompensas de temporada", "Objetivos con fecha límite y viabilidad"),
        MoreItem("roster", "Roster", "Todos tus personajes y sus intentos de montura"),
        MoreItem("settings", "Ajustes", "Cuenta, región, actualización, datos y diagnóstico"),
    )
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        items(items) { item ->
            Card(Modifier.fillMaxWidth().clickable { onNavigate(item.route) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
