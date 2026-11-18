package ru.anidesk.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.anidesk.app.ui.navigation.AniDeskApp
import ru.anidesk.app.ui.theme.AniDeskTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_RELEASE_ID = "release_id"
    }

    private val notificationReleaseId = mutableIntStateOf(0)

    /**
     * Счётчик событий «открыть релиз по уведомлению». Нужен, чтобы повторный тап
     * по тому же уведомлению снова открывал релиз: одного releaseId недостаточно,
     * потому что значение состояния при этом не меняется.
     */
    private val notificationEvent = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // При пересоздании активности (поворот) intent переиспользуется — не открываем релиз заново.
        if (savedInstanceState == null) {
            consumeNotificationIntent(intent)
        }
        val app = application as AniDeskApplication
        setContent {
            val themeMode by app.settingsStore.theme.collectAsStateWithLifecycle(initialValue = 0)

            AniDeskTheme(themeMode = themeMode) {
                AniDeskApp(
                    api = app.api,
                    sessionStore = app.sessionStore,
                    settingsStore = app.settingsStore,
                    notificationReleaseId = notificationReleaseId.intValue,
                    notificationEvent = notificationEvent.intValue,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNotificationIntent(intent)
    }

    /** Забирает releaseId из intent один раз и превращает его в событие навигации. */
    private fun consumeNotificationIntent(intent: Intent?) {
        val releaseId = intent?.getIntExtra(EXTRA_RELEASE_ID, 0) ?: 0
        if (releaseId <= 0) return
        intent?.removeExtra(EXTRA_RELEASE_ID)
        notificationReleaseId.intValue = releaseId
        notificationEvent.intValue += 1
    }
}
