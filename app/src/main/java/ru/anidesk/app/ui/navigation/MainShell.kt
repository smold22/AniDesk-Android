package ru.anidesk.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.core.settings.SettingsStore
import ru.anidesk.app.ui.components.isTv
import ru.anidesk.app.ui.screens.AccountScreen
import ru.anidesk.app.ui.screens.BookmarksScreen
import ru.anidesk.app.ui.screens.DiscoverScreen
import ru.anidesk.app.ui.screens.FavoritesScreen
import ru.anidesk.app.ui.screens.HistoryScreen
import ru.anidesk.app.ui.screens.HomeScreen
import ru.anidesk.app.ui.screens.NotificationsScreen
import ru.anidesk.app.ui.screens.ProfileScreen
import ru.anidesk.app.ui.screens.SettingsScreen
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.NavBarBackground
import ru.anidesk.app.ui.theme.ThirdText

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Главная", Icons.Filled.Home),
    Tab("discover", "Смотрят", Icons.Filled.Explore),
    Tab("bookmarks", "Закладки", Icons.Filled.Bookmarks),
    Tab("settings", "Настройки", Icons.Filled.Settings),
    Tab("profile", "Профиль", Icons.Filled.Person),
)

private val LOGIN_TAB = Tab("login", "Вход", Icons.AutoMirrored.Filled.Login)

@Composable
fun MainShell(
    api: AnixartApi,
    sessionStore: SessionStore,
    settingsStore: SettingsStore,
    token: String?,
    authSkipped: Boolean,
    onOpenSearch: () -> Unit,
    onOpenRelease: (Int) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tv = isTv()

    if (tv) {
        TvMainShell(
            navController = navController,
            currentRoute = currentRoute,
            token = token,
            authSkipped = authSkipped,
            api = api,
            sessionStore = sessionStore,
            settingsStore = settingsStore,
            onOpenSearch = onOpenSearch,
            onOpenRelease = onOpenRelease,
        )
        return
    }

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
        MainNavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            startDestination = "home",
            api = api,
            sessionStore = sessionStore,
            settingsStore = settingsStore,
            onOpenSearch = onOpenSearch,
            onOpenRelease = onOpenRelease,
        )
    }
}

@Composable
private fun TvMainShell(
    navController: androidx.navigation.NavHostController,
    currentRoute: String?,
    token: String?,
    authSkipped: Boolean,
    api: AnixartApi,
    sessionStore: SessionStore,
    settingsStore: SettingsStore,
    onOpenSearch: () -> Unit,
    onOpenRelease: (Int) -> Unit,
) {
    LaunchedEffect(token, currentRoute) {
        when {
            token == null && currentRoute == "profile" ->
                navController.navigate("login") {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            token != null && currentRoute == "login" ->
                navController.navigate("profile") {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
        }
    }

    val tabs = if (token == null) {
        TABS.filter { it.route != "profile" } + LOGIN_TAB
    } else {
        TABS
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight()
                .background(NavBarBackground)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "AniDesk",
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = Carmine,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Carmine.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) Carmine else ThirdText,
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        text = tab.label,
                        fontSize = 12.sp,
                        color = if (selected) Carmine else MainText,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        MainNavHost(
            navController = navController,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            startDestination = if (token != null || authSkipped) "home" else "login",
            api = api,
            sessionStore = sessionStore,
            settingsStore = settingsStore,
            onOpenSearch = onOpenSearch,
            onOpenRelease = onOpenRelease,
        )
    }
}

@Composable
private fun MainNavHost(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String,
    api: AnixartApi,
    sessionStore: SessionStore,
    settingsStore: SettingsStore,
    onOpenSearch: () -> Unit,
    onOpenRelease: (Int) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable("login") {
            AccountScreen(
                api = api,
                sessionStore = sessionStore,
                onLoggedIn = {
                    navController.navigate("profile") {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onSkip = {
                    navController.navigate("home") {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable("home") {
            HomeScreen(
                api = api,
                settingsStore = settingsStore,
                onOpenRelease = onOpenRelease,
                onOpenSearch = onOpenSearch,
                onOpenNotifications = {
                    navController.navigate("notifications") { launchSingleTop = true }
                },
            )
        }
        composable("notifications") {
            NotificationsScreen(
                api = api,
                onOpenRelease = onOpenRelease,
                onBack = { navController.popBackStack() },
            )
        }
        composable("discover") {
            DiscoverScreen(
                api = api,
                onOpenRelease = onOpenRelease,
                onBack = { navController.navigate("home") { launchSingleTop = true } },
            )
        }
        composable("bookmarks") {
            BookmarksScreen(
                api = api,
                settingsStore = settingsStore,
                onOpenRelease = onOpenRelease,
                onBack = { navController.navigate("home") { launchSingleTop = true } },
            )
        }
        composable("settings") {
            SettingsScreen(
                api = api,
                settingsStore = settingsStore,
                onBack = { navController.navigate("home") { launchSingleTop = true } },
            )
        }
        composable("profile") {
            ProfileScreen(
                api = api,
                sessionStore = sessionStore,
                onOpenRelease = onOpenRelease,
                onBack = { navController.navigate("home") { launchSingleTop = true } },
                onOpenLogin = {
                    navController.navigate("login") {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenHistory = {
                    navController.navigate("history") { launchSingleTop = true }
                },
                onOpenFavorites = {
                    navController.navigate("favorites") { launchSingleTop = true }
                },
            )
        }
        composable("history") {
            HistoryScreen(
                api = api,
                onOpenRelease = onOpenRelease,
                onBack = { navController.popBackStack() },
            )
        }
        composable("favorites") {
            FavoritesScreen(
                api = api,
                onOpenRelease = onOpenRelease,
                onBack = { navController.popBackStack() },
            )
        }
    }
}