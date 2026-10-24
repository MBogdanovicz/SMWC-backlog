package com.capivaraec.smwc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.capivaraec.smwc.app.ui.AchievementCommentsScreen
import com.capivaraec.smwc.app.ui.AchievementsScreen
import com.capivaraec.smwc.app.ui.BacklogScreen
import com.capivaraec.smwc.app.ui.DetailScreen
import com.capivaraec.smwc.app.ui.RaSetScreen
import com.capivaraec.smwc.app.ui.SearchScreen
import com.capivaraec.smwc.app.ui.SettingsScreen
import com.capivaraec.smwc.app.ui.SyncViewModel
import com.capivaraec.smwc.app.ui.components.AppMark
import com.capivaraec.smwc.app.ui.components.LocalSnackbar
import com.capivaraec.smwc.app.ui.components.isWideLayout
import com.capivaraec.smwc.app.ui.theme.SmwcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SmwcApplication
        setContent {
            val useSystemColors by app.preferences.useSystemColors.collectAsStateWithLifecycle()
            SmwcTheme(useSystemColors) {
                AppRoot(app)
            }
        }
    }
}

@Composable
private fun AppRoot(app: SmwcApplication) {
    val sync: SyncViewModel = viewModel { SyncViewModel(app) }

    // Google asks for Drive consent once; the sync resumes when the user comes back.
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        sync.onConsentResult(app.driveAuthorizer.handleConsentResult(result.data))
    }
    LaunchedEffect(Unit) {
        sync.consentRequests.collect { pending ->
            consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        }
    }
    LifecycleResumeEffect(Unit) {
        sync.syncNow()
        onPauseOrDispose { }
    }

    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val topLevel = TOP_LEVEL.any { it.route == route }
    val wide = isWideLayout()
    val snackbar = remember { SnackbarHostState() }

    fun go(destination: String) = nav.navigate(destination) { popUpTo("search"); launchSingleTop = true }

    // Detail panes beside the lists on wide screens; on narrow ones the same screens open full size.
    val hackPane: @Composable (Long, (Long) -> Unit) -> Unit = { id, select ->
        DetailScreen(
            app,
            hackId = id,
            onBack = null,
            onOpenRaSet = { gameId -> nav.navigate("ra/$gameId?hackId=$id&fromDetails=true") },
            onReplaceWithHack = select,
        )
    }
    val setPane: @Composable (Long, Long?) -> Unit = { gameId, hackId ->
        RaSetScreen(
            app,
            gameId,
            hackId,
            onOpenHack = hackId?.takeIf { it >= 0 }?.let { { nav.navigate("hack/$it") } },
            onOpenAchievement = { nav.navigate("ra/$gameId/achievement/$it") },
            onBack = null,
        )
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (topLevel && !wide) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        TOP_LEVEL.forEach { item ->
                            NavigationBarItem(
                                selected = route == item.route,
                                onClick = { go(item.route) },
                                icon = { Icon(if (route == item.route) item.selectedIcon else item.icon, contentDescription = null) },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.padding(padding).consumeWindowInsets(padding)) {
                if (topLevel && wide) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        header = { AppMark(Modifier.padding(vertical = 12.dp)) },
                    ) {
                        TOP_LEVEL.forEach { item ->
                            NavigationRailItem(
                                selected = route == item.route,
                                onClick = { go(item.route) },
                                icon = { Icon(if (route == item.route) item.selectedIcon else item.icon, contentDescription = null) },
                                // The rail is narrow: long labels ("Achievements") shrink to fit.
                                label = { Text(item.label, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp)) },
                                colors = NavigationRailItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(
                            selected = false,
                            onClick = { nav.navigate("settings") },
                            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            label = { Text("Settings") },
                        )
                    }
                }
                NavHost(nav, startDestination = "backlog", modifier = Modifier.weight(1f)) {
                    composable("search") {
                        SearchScreen(
                            app,
                            onOpenHack = { nav.navigate("hack/$it") },
                            onOpenSettings = { nav.navigate("settings") },
                            hackPane = hackPane,
                        )
                    }
                    composable("backlog") {
                        BacklogScreen(app, sync, onOpenHack = { nav.navigate("hack/$it") }, hackPane = hackPane)
                    }
                    composable("achievements") {
                        AchievementsScreen(
                            app,
                            onOpenSet = { gameId, hackId -> nav.navigate("ra/$gameId?hackId=$hackId") },
                            onOpenSettings = { nav.navigate("settings") },
                            setPane = setPane,
                        )
                    }
                    composable("settings") {
                        SettingsScreen(app, onBack = { nav.popBackStack() })
                    }
                    composable("hack/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments!!.getLong("id")
                        DetailScreen(
                            app,
                            hackId = id,
                            onBack = { nav.popBackStack() },
                            onOpenRaSet = { gameId -> nav.navigate("ra/$gameId?hackId=$id&fromDetails=true") },
                            onReplaceWithHack = { newId ->
                                nav.popBackStack()
                                nav.navigate("hack/$newId")
                            },
                        )
                    }
                    composable(
                        "ra/{gameId}?hackId={hackId}&fromDetails={fromDetails}",
                        arguments = listOf(
                            navArgument("gameId") { type = NavType.LongType },
                            // Long.MIN_VALUE = no hack (ids of hacks added by name are negative, but never that).
                            navArgument("hackId") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                            navArgument("fromDetails") { type = NavType.BoolType; defaultValue = false },
                        ),
                    ) { entry ->
                        val hackId = entry.arguments!!.getLong("hackId").takeIf { it != Long.MIN_VALUE }
                        val fromDetails = entry.arguments!!.getBoolean("fromDetails")
                        val gameId = entry.arguments!!.getLong("gameId")
                        RaSetScreen(
                            app,
                            gameId = gameId,
                            hackId = hackId,
                            // Not when opened from the hack's details (going back returns there), nor for
                            // hacks added by name (negative ids), which have no details page.
                            onOpenHack = if (hackId != null && hackId >= 0 && !fromDetails) ({ nav.navigate("hack/$hackId") }) else null,
                            onOpenAchievement = { nav.navigate("ra/$gameId/achievement/$it") },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(
                        "ra/{gameId}/achievement/{achievementId}",
                        arguments = listOf(
                            navArgument("gameId") { type = NavType.LongType },
                            navArgument("achievementId") { type = NavType.LongType },
                        ),
                    ) { entry ->
                        AchievementCommentsScreen(
                            app,
                            gameId = entry.arguments!!.getLong("gameId"),
                            achievementId = entry.arguments!!.getLong("achievementId"),
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TOP_LEVEL = listOf(
    TopLevel("search", "Search", Icons.Outlined.Search, Icons.Filled.Search),
    TopLevel("backlog", "My hacks", Icons.AutoMirrored.Outlined.FormatListBulleted, Icons.AutoMirrored.Filled.FormatListBulleted),
    TopLevel("achievements", "Achievements", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents),
)
