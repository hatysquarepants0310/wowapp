package com.azeroth.companion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        Destination("events", R.string.nav_events, Icons.Filled.Timer),
        Destination("weekly", R.string.nav_weekly, Icons.Filled.Checklist),
        Destination("progression", R.string.nav_progression, Icons.Filled.TrendingUp),
        Destination("seasonal", R.string.nav_seasonal, Icons.Filled.CalendarMonth),
        Destination("settings", R.string.nav_settings, Icons.Filled.Settings),
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
            composable("progression") { ProgressionScreen() }
            composable("seasonal") { SeasonalScreen() }
            composable("roster") { RosterScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
