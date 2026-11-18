package ru.anidesk.app

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.core.notifications.Notifications
import ru.anidesk.app.core.settings.SettingsStore
import ru.anidesk.app.core.update.UpdateManager
import java.io.File

class AniDeskApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    val api: AnixartApi by lazy {
        val created = AnixartApi(SettingsStore.apiBaseUrl(SettingsStore.DEFAULT_API_ENDPOINT)) { workingBaseUrl ->
            val workingHost = SettingsStore.apiHost(workingBaseUrl)
            appScope.launch {
                settingsStore.setApiEndpoint(workingHost)
                Log.i(TAG, "API host switched to $workingHost")
            }
        }
        // Не блокируем главный поток на чтении DataStore: стартуем с основного хоста
        // и сразу применяем сохранённый пользователем эндпоинт.
        appScope.launch {
            val host = settingsStore.apiEndpoint.first()
            if (host != SettingsStore.DEFAULT_API_ENDPOINT) {
                created.baseUrl = SettingsStore.apiBaseUrl(host)
                Log.i(TAG, "API endpoint restored: $host")
            }
        }
        created
    }

    val sessionStore: SessionStore by lazy { SessionStore(this) }

    /**
     * Один экземпляр на приложение: иначе состояние загрузки обновления теряется
     * при уходе с экрана настроек.
     */
    val updateManager: UpdateManager by lazy { UpdateManager(this) }

    override fun onCreate() {
        super.onCreate()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(this)
                .components { add(OkHttpNetworkFetcherFactory()) }
                .crossfade(true)
                .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
                .build()
        )
        cleanUpdateCache()
        appScope.launch {
            if (settingsStore.newEpisodesEnabled.first()) {
                Notifications.schedule(this@AniDeskApplication)
                Notifications.runImmediately(this@AniDeskApplication)
            }
        }
    }

    private fun cleanUpdateCache() {
        runCatching {
            File(cacheDir, "apk").deleteRecursively()
        }
    }

    private companion object {
        const val TAG = "AniDeskApplication"
    }
}
