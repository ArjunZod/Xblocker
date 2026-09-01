package com.antigravity.shieldx.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.antigravity.shieldx.core.security.SecurityManager
import com.antigravity.shieldx.ui.admin.DeviceOwnerSetupScreen
import com.antigravity.shieldx.ui.assistant.AssistantChatScreen
import com.antigravity.shieldx.ui.assistant.TarziControlScreen
import com.antigravity.shieldx.ui.automation.AutomationListScreen
import com.antigravity.shieldx.ui.blocked.BlockedEventsScreen
import com.antigravity.shieldx.ui.dashboard.DashboardScreen
import com.antigravity.shieldx.ui.diagnostics.DiagnosticsScreen
import com.antigravity.shieldx.ui.home.HomeScreen
import com.antigravity.shieldx.ui.music.NowPlayingScreen
import com.antigravity.shieldx.ui.music.MiniPlayerBar
import com.antigravity.shieldx.ui.music.MusicHomeScreen
import com.antigravity.shieldx.ui.navigation.Screen
import com.antigravity.shieldx.ui.policies.AppPoliciesScreen
import com.antigravity.shieldx.ui.policies.PolicyScreen
import com.antigravity.shieldx.ui.theme.*

class MainActivity : ComponentActivity() {

    private lateinit var securityManager: SecurityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        securityManager = SecurityManager.getInstance(applicationContext)

        setContent {
            TarziTheme {
                TarziApp(securityManager = securityManager)
            }
        }
    }
}

/** The four places worth having a permanent destination for. */
private data class Destination(
    val screen: Screen,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector
)

private val destinations = listOf(
    Destination(Screen.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    Destination(Screen.Music, "Music", Icons.Filled.MusicNote, Icons.Outlined.MusicNote),
    Destination(Screen.Protection, "Protection", Icons.Filled.Shield, Icons.Outlined.Shield),
    Destination(Screen.TarziControl, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun TarziApp(securityManager: SecurityManager) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val playbackState by securityManager.musicController.playbackStateFlow.collectAsState()
    var showFullPlayer by remember { mutableStateOf(false) }

    // The bar is for top-level destinations only; a detail screen keeps the
    // bar visible but shows nothing selected rather than hiding it and making
    // the layout jump.
    val showBottomBar = destinations.any { it.screen.route == currentRoute } ||
        currentRoute == Screen.Assistant.route

    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            if (showBottomBar) {
                Column {
                    if (playbackState.currentTrack != null) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            musicGraph = securityManager.musicGraph,
                            onExpandClick = { showFullPlayer = true }
                        )
                    }
                    BottomBar(navController = navController, currentRoute = currentRoute)
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Canvas)
        ) {
            NavHost(navController = navController, startDestination = Screen.Home.route) {

                composable(Screen.Home.route) {
                    HomeScreen(
                        securityManager = securityManager,
                        onNavigateToAssistant = { navController.navigate(Screen.Assistant.route) },
                        onNavigateToMusic = { navController.navigateTop(Screen.Music.route) },
                        onNavigateToProtection = { navController.navigateTop(Screen.Protection.route) }
                    )
                }

                composable(Screen.Assistant.route) {
                    AssistantChatScreen(
                        securityManager = securityManager,
                        onNavigateToMusic = { navController.navigateTop(Screen.Music.route) },
                        onNavigateToProtection = { navController.navigateTop(Screen.Protection.route) }
                    )
                }

                composable(Screen.Music.route) {
                    MusicHomeScreen(
                        musicGraph = securityManager.musicGraph,
                        onOpenFullPlayer = { showFullPlayer = true }
                    )
                }

                composable(Screen.Protection.route) {
                    DashboardScreen(
                        securityManager = securityManager,
                        onNavigateToSetup = { navController.navigate(Screen.DeviceOwnerSetup.route) },
                        onNavigateToLogs = { navController.navigate(Screen.BlockedLogs.route) },
                        onNavigateToPolicies = { navController.navigate(Screen.Policies.route) }
                    )
                }

                composable(Screen.TarziControl.route) {
                    TarziControlScreen(
                        securityManager = securityManager,
                        onNavigateToAutomations = { navController.navigate(Screen.Automations.route) },
                        onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
                        onNavigateToApps = { navController.navigate(Screen.Apps.route) }
                    )
                }

                // Detail screens, reached from a row rather than the bar.
                composable(Screen.Automations.route) {
                    AutomationListScreen(
                        securityManager = securityManager,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Policies.route) {
                    PolicyScreen(
                        securityManager = securityManager,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Apps.route) {
                    AppPoliciesScreen(
                        securityManager = securityManager,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.BlockedLogs.route) {
                    BlockedEventsScreen(
                        securityManager = securityManager,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Diagnostics.route) {
                    DiagnosticsScreen(
                        securityManager = securityManager,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.DeviceOwnerSetup.route) {
                    DeviceOwnerSetupScreen(
                        securityManager = securityManager,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            if (showFullPlayer) {
                NowPlayingScreen(
                    playbackState = playbackState,
                    musicGraph = securityManager.musicGraph,
                    onDismiss = { showFullPlayer = false }
                )
            }
        }
    }
}

/**
 * A plain bar: icon plus label, the selected one tinted. No pills, no
 * individual containers, no colour per destination.
 */
@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Border)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Canvas)
                .navigationBarsPadding()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { destination ->
                val selected = currentRoute == destination.screen.route
                val tint by animateColorAsState(
                    targetValue = if (selected) Accent else TextTertiary,
                    animationSpec = tween(Motion.fast),
                    label = "navTint"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { navController.navigateTop(destination.screen.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (selected) destination.iconSelected else destination.iconUnselected,
                        contentDescription = destination.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = tint
                    )
                }
            }
        }
    }
}

/** Switch top-level destination without stacking duplicates. */
private fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
