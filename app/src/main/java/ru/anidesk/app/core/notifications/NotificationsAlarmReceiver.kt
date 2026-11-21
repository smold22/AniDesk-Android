package ru.anidesk.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.anidesk.app.core.settings.SettingsStore

/**
 * Резервный механизм фоновой проверки уведомлений на устройствах с агрессивными
 * оболочками (HiOS, MIUI и т.п.), которые убивают задачи WorkManager:
 * системный будильник будит процесс, приёмник ставит одноразовый воркер
 * и планирует следующий тик.
 */
class NotificationsAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Notifications.ACTION_CHECK_NOTIFICATIONS) return
        Log.i(TAG, "alarm tick received")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                if (!SettingsStore(appContext).newEpisodesEnabled.first()) {
                    Log.i(TAG, "alarm tick skipped: notifications disabled, cancelling alarm")
                    Notifications.cancelAlarm(appContext)
                    return@launch
                }
                Notifications.runImmediately(appContext)
                Notifications.scheduleAlarm(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "alarm tick failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NotificationsAlarm"
    }
}
