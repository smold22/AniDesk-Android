package ru.anidesk.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.core.settings.SettingsStore
import ru.anidesk.app.ui.screens.LoginScreen
import ru.anidesk.app.ui.screens.PlayerScreen
import ru.anidesk.app.ui.screens.ReleaseScreen
import ru.anidesk.app.ui.screens.SearchScreen

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val SEARCH = "search"
    const val RELEASE = "release/{releaseId}"
    const val PLAYER = "player/{releaseId}/{dubberId}/{sourceId}/{position}/{sourceName}"

    fun release(releaseId: Int) = "release/$releaseId"

    fun player(
        releaseId: Int,
        dubberId: Int,
        sourceId: Int,
        position: Int,
        sourceName: String,
    ) = "player/$releaseId/$dubberId/$sourceId/$position/$sourceName"
}

@Composable
fun AniDeskApp(api: AnixartApi, sessionStore: SessionStore, settingsStore: SettingsStore) {
    val navController = rememberNavController()
    val token by sessionStore.token.collectAsStateWithLifecycle(initialValue = null)
    var sessionReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        sessionStore.token.first()
        sessionReady = true
    }

    LaunchedEffect(token, sessionReady) {
        if (!sessionReady) return@LaunchedEffect
        api.token = token
        if (token != null && navController.currentDestination?.route == Routes.LOGIN) {
            navController.navigate(Routes.MAIN) {
                popUpTo(Routes.LOGIN) { inclusive = true }
            }
        }
        if (token == null && navController.currentDestination?.route != Routes.LOGIN) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    if (!sessionReady) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (token != null) Routes.MAIN else Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(api = api, sessionStore = sessionStore)
        }

        composable(Routes.MAIN) {
            MainShell(
                api = api,
                sessionStore = sessionStore,
                settingsStore = settingsStore,
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenRelease = { id -> navController.navigate(Routes.release(id)) },
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                api = api,
                settingsStore = settingsStore,
                onBack = { navController.popBackStack() },
                onOpenRelease = { id -> navController.navigate(Routes.release(id)) },
            )
        }

        composable(
            route = Routes.RELEASE,
            arguments = listOf(navArgument("releaseId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val releaseId = backStackEntry.arguments?.getInt("releaseId") ?: 0
            ReleaseScreen(
                api = api,
                settingsStore = settingsStore,
                releaseId = releaseId,
                onBack = { navController.popBackStack() },
                onOpenRelease = { id -> navController.navigate(Routes.release(id)) },
                onPlay = { id, dubber, source, position, sourceName ->
                    navController.navigate(Routes.player(id, dubber, source, position, sourceName))
                },
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("releaseId") { type = NavType.IntType },
                navArgument("dubberId") { type = NavType.IntType },
                navArgument("sourceId") { type = NavType.IntType },
                navArgument("position") { type = NavType.IntType },
                navArgument("sourceName") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            PlayerScreen(
                api = api,
                releaseId = args?.getInt("releaseId") ?: 0,
                dubberId = args?.getInt("dubberId") ?: 0,
                sourceId = args?.getInt("sourceId") ?: 0,
                startPosition = args?.getInt("position") ?: 0,
                sourceName = args?.getString("sourceName") ?: "Kodik",
                settingsStore = settingsStore,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
