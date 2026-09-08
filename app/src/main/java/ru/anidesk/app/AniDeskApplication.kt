package ru.anidesk.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.core.settings.SettingsStore

class AniDeskApplication : Application() {

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    val api: AnixartApi by lazy {
        val host = runBlocking { settingsStore.apiEndpoint.first() }
        AnixartApi(SettingsStore.apiBaseUrl(host))
    }

    val sessionStore: SessionStore by lazy { SessionStore(this) }

    override fun onCreate() {
        super.onCreate()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(this)
                .components { add(OkHttpNetworkFetcherFactory()) }
                .crossfade(true)
                .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
                .build()
        )
    }
}
