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
 * После перезагрузки устройства перепланирует будильник проверки уведомлений:
 * AlarmManager теряет будильники приложения при выключении питания.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TAG, "boot completed: rescheduling notifications alarm")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                if (SettingsStore(appContext).newEpisodesEnabled.first()) {
                    Notifications.scheduleAlarm(appContext)
                }
            } catch (e: Exception) {
                Log.w(TAG, "boot reschedule failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NotificationsBoot"
    }
}
