package ru.anidesk.app.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
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
import ru.anidesk.app.player.PlayerActivity
import ru.anidesk.app.ui.components.isTv
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
fun AniDeskApp(
    api: AnixartApi,
    sessionStore: SessionStore,
    settingsStore: SettingsStore,
    notificationReleaseId: Int = 0,
) {
    val navController = rememberNavController()
    var token by remember { mutableStateOf<String?>(null) }
    var authSkipped by remember { mutableStateOf(false) }
    var tokenLoaded by remember { mutableStateOf(false) }
    var authSkippedLoaded by remember { mutableStateOf(false) }
    val tv = isTv()
    val context = LocalContext.current

    var permissionRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(token) {
        if (!tv && !permissionRequested && token != null && Build.VERSION.SDK_INT >= 33) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(notificationReleaseId) {
        if (notificationReleaseId > 0 &&
            navController.currentDestination?.route != Routes.RELEASE
        ) {
            navController.navigate(Routes.release(notificationReleaseId)) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(Unit) {
        sessionStore.token.collect {
            token = it
            tokenLoaded = true
        }
    }

    LaunchedEffect(Unit) {
        sessionStore.authSkipped.collect {
            authSkipped = it
            authSkippedLoaded = true
        }
    }

    LaunchedEffect(token, tokenLoaded) {
        if (!tokenLoaded) return@LaunchedEffect
        api.token = token
        if (tv) return@LaunchedEffect
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

    if (!tokenLoaded || !authSkippedLoaded) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (token != null || tv) Routes.MAIN else Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(api = api, sessionStore = sessionStore)
        }

        composable(Routes.MAIN) {
            MainShell(
                api = api,
                sessionStore = sessionStore,
                settingsStore = settingsStore,
                token = token,
                authSkipped = authSkipped,
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
            val context = LocalContext.current
            ReleaseScreen(
                api = api,
                settingsStore = settingsStore,
                releaseId = releaseId,
                onBack = { navController.popBackStack() },
                onOpenRelease = { id -> navController.navigate(Routes.release(id)) },
                onPlay = { id, dubber, source, position, sourceName ->
                    if (tv) {
                        PlayerActivity.start(context, id, dubber, source, position, sourceName)
                    } else {
                        navController.navigate(Routes.player(id, dubber, source, position, sourceName))
                    }
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
