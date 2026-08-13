package ru.anidesk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.anidesk.app.ui.navigation.AniDeskApp
import ru.anidesk.app.ui.theme.AniDeskTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AniDeskApplication
        setContent {
            val themeMode by app.settingsStore.theme.collectAsStateWithLifecycle(initialValue = 0)
            AniDeskTheme(themeMode = themeMode) {
                AniDeskApp(api = app.api, sessionStore = app.sessionStore, settingsStore = app.settingsStore)
            }
        }
    }
}
