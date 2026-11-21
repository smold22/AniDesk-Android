package ru.anidesk.app.core.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ru.anidesk.app.MainActivity
import ru.anidesk.app.R
import java.util.concurrent.TimeUnit

object Notifications {

    private const val TAG = "Notifications"

    const val CHANNEL_NEW_EPISODES = "anidesk_notifications"
    const val ACTION_CHECK_NOTIFICATIONS = "ru.anidesk.app.ACTION_CHECK_NOTIFICATIONS"
    const val EXTRA_RELEASE_ID = MainActivity.EXTRA_RELEASE_ID
    private const val WORK_NEW_EPISODES = "episode_notifications"
    private const val WORK_NEW_EPISODES_IMMEDIATE = "episode_notifications_immediate"
    private const val ALARM_REQUEST_CODE = 42
    private const val CHECK_INTERVAL_MINUTES = 30L

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_NEW_EPISODES) == null) {
                val channel = NotificationChannel(
                    CHANNEL_NEW_EPISODES,
                    "Уведомления",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Новые серии, похожие релизы, комментарии и заявки в друзья"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /** Планирует периодическую проверку уведомлений (мин. интервал 15 мин, используем 30). */
    fun schedule(context: Context) {
        ensureChannel(context)
        scheduleAlarm(context)
        val request = PeriodicWorkRequestBuilder<EpisodeNotificationsWorker>(
            30, TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        ).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NEW_EPISODES, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NEW_EPISODES)
        cancelAlarm(context)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NEW_EPISODES_IMMEDIATE)
    }

    /**
     * Резервный тик через AlarmManager: переживает выгрузку процесса и Doze
     * на оболочках, убивающих WorkManager (HiOS, MIUI и т.п.).
     */
    fun scheduleAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(CHECK_INTERVAL_MINUTES)
        val pi = alarmPendingIntent(context)
        try {
            val exactAllowed = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            // Разрешение отозвано между canScheduleExactAlarms() и setExact() — ставим неточный.
            Log.w(TAG, "exact alarm denied, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmPendingIntent(context))
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NotificationsAlarmReceiver::class.java).apply {
            action = ACTION_CHECK_NOTIFICATIONS
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Одноразовая немедленная проверка уведомлений (не мешает периодической). */
    fun runImmediately(context: Context) {
        ensureChannel(context)
        val request = OneTimeWorkRequestBuilder<EpisodeNotificationsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NEW_EPISODES_IMMEDIATE,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Показывает системное уведомление. При [releaseId] > 0 тап открывает экран релиза,
     * иначе — просто открывает приложение.
     *
     * @return true, если уведомление действительно было отправлено.
     */
    fun show(
        context: Context,
        id: Int,
        title: String,
        text: String,
        releaseId: Int? = null,
    ): Boolean {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "show skipped: POST_NOTIFICATIONS permission is not granted")
            return false
        }
        // Выключенный пользователем канал не бросает исключение при notify(),
        // поэтому проверяем его явно — иначе пуш считается «показанным».
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(CHANNEL_NEW_EPISODES)
            if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) {
                Log.w(TAG, "show skipped: notification channel is disabled")
                return false
            }
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (releaseId != null) putExtra(EXTRA_RELEASE_ID, releaseId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_EPISODES)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        }.getOrElse { e ->
            Log.w(TAG, "show failed: id=$id", e)
            false
        }
    }
}