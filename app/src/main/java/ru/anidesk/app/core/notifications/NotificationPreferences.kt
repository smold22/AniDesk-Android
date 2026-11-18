package ru.anidesk.app.core.notifications

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.settings.SettingsStore

/**
 * Серверные настройки уведомлений Anixart по умолчанию выключены,
 * поэтому серверные фиды уведомлений остаются пустыми. Настраиваем их один раз
 * для текущей версии конфигурации, когда пользователь включает уведомления.
 */
object NotificationPreferences {

    private const val TAG = "NotificationPreferences"
    private const val CONFIG_VERSION = 2
    private val DEFAULT_STATUSES = (0..5).toList()
    private val mutex = Mutex()

    suspend fun ensureConfigured(api: AnixartApi, settings: SettingsStore) = mutex.withLock {
        if (settings.getServerNotificationPrefsVersion() >= CONFIG_VERSION) {
            Log.i(TAG, "already configured (version ${settings.getServerNotificationPrefsVersion()})")
            return@withLock
        }

        val prefs = api.notificationPreferences()
        Log.i(
            TAG,
            "server prefs: episode=${prefs.isEpisodeNotificationsEnabled} " +
                "first=${prefs.isFirstEpisodeNotificationsEnabled} " +
                "related=${prefs.isRelatedReleaseNotificationsEnabled} " +
                "statuses=${prefs.statusPreferences.size} types=${prefs.typePreferences.size}",
        )

        if (!prefs.isEpisodeNotificationsEnabled) {
            api.editNotificationPreference("episode")
        }
        if (!prefs.isFirstEpisodeNotificationsEnabled) {
            api.editNotificationPreference("episode/first")
        }
        if (!prefs.isRelatedReleaseNotificationsEnabled) {
            api.editNotificationPreference("related/release")
        }

        api.editNotificationStatusPreferences(DEFAULT_STATUSES)

        val typeIds = api.types()
            .map { it.id }
            .filter { it > 0 }
            .distinct()
            .ifEmpty { api.popularTypeIds() }
        if (typeIds.isNotEmpty()) {
            api.editNotificationTypePreferences(typeIds)
        }

        settings.setServerNotificationPrefsVersion(CONFIG_VERSION)
    }
}
