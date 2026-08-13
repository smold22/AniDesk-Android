package ru.anidesk.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.core.settings.SettingsStore

class AniDeskApplication : Application() {

    val api: AnixartApi by lazy { AnixartApi() }

    val sessionStore: SessionStore by lazy { SessionStore(this) }

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(this)
                .components { add(OkHttpNetworkFetcherFactory()) }
                .build()
        )
    }
}
