package ru.anidesk.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.anidesk.app.ui.navigation.AniDeskApp
import ru.anidesk.app.ui.theme.AniDeskTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_RELEASE_ID = "release_id"
    }

    private val notificationReleaseId = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationReleaseId.intValue = intent.getIntExtra(EXTRA_RELEASE_ID, 0)
        val app = application as AniDeskApplication
        setContent {
            val releaseId = remember { notificationReleaseId.intValue }
            val themeMode by app.settingsStore.theme.collectAsStateWithLifecycle(initialValue = 0)
            val context = LocalContext.current

            // На Android 13+ уведомления молча не показываются без явного разрешения.
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            AniDeskTheme(themeMode = themeMode) {
                AniDeskApp(
                    api = app.api,
                    sessionStore = app.sessionStore,
                    settingsStore = app.settingsStore,
                    notificationReleaseId = releaseId,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationReleaseId.intValue = intent.getIntExtra(EXTRA_RELEASE_ID, 0)
    }
}
