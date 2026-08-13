package ru.anidesk.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.core.settings.SettingsStore
import ru.anidesk.app.ui.screens.BookmarksScreen
import ru.anidesk.app.ui.screens.DiscoverScreen
import ru.anidesk.app.ui.screens.HomeScreen
import ru.anidesk.app.ui.screens.ProfileScreen
import ru.anidesk.app.ui.screens.SettingsScreen
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.NavBarBackground
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Главная", Icons.Filled.Home),
    Tab("discover", "Смотрят", Icons.Filled.Explore),
    Tab("bookmarks", "Закладки", Icons.Filled.Bookmarks),
    Tab("settings", "Настройки", Icons.Filled.Settings),
    Tab("profile", "Профиль", Icons.Filled.Person),
)

@Composable
fun MainShell(
    api: AnixartApi,
    sessionStore: SessionStore,
    settingsStore: SettingsStore,
    onOpenSearch: () -> Unit,
    onOpenRelease: (Int) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            NavigationBar(
                containerColor = NavBarBackground,
                tonalElevation = 0.dp,
            ) {
                TABS.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                color = if (selected) Carmine else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Carmine,
                            selectedTextColor = Carmine,
                            indicatorColor = Carmine.copy(alpha = 0.15f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable("home") {
                HomeScreen(
                    api = api,
                    settingsStore = settingsStore,
                    onOpenRelease = onOpenRelease,
                    onOpenSearch = onOpenSearch,
                )
            }
            composable("discover") {
                DiscoverScreen(api = api, onOpenRelease = onOpenRelease)
            }
            composable("bookmarks") {
                BookmarksScreen(api = api, settingsStore = settingsStore, onOpenRelease = onOpenRelease)
            }
            composable("settings") {
                SettingsScreen(settingsStore = settingsStore)
            }
            composable("profile") {
                ProfileScreen(api = api, sessionStore = sessionStore, onOpenRelease = onOpenRelease)
            }
        }
    }
}
