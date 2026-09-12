package ru.anidesk.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.anidesk.app.ui.components.TvKeyRouter
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            TvKeyRouter.userInteracted = true
        }
        if (event.action == KeyEvent.ACTION_DOWN && isTvUiMode()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val handler = TvKeyRouter.fieldDpadDown
                    if (handler != null && handler()) return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    val handler = TvKeyRouter.fieldDpadUp
                    if (handler != null && handler()) return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isTvUiMode(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
}
