package ru.anidesk.app.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.PageableResponse
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.core.settings.SettingsStore

/**
 * Фоновая проверка уведомлений (аналог пуш-уведомлений AnixartM).
 * В AnixartM уведомления приходят с сервера по FCM; здесь тот же фид
 * опрашивается локально через WorkManager:
 *   - notification/episodes            — новые серии
 *   - notification/related/release     — новые серии похожих тайтлов
 * Первый запуск молча запоминает id всех текущих уведомлений (без спама),
 * последующие — показывают только появившиеся с прошлой проверки.
 */
class EpisodeNotificationsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private data class Item(
        val key: String,
        val notificationId: Int,
        val releaseId: Int?,
        val title: String,
        val text: String,
    )

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        if (!settings.newEpisodesEnabled.first()) return Result.success()
        val token = SessionStore(applicationContext).token.first() ?: return Result.success()

        val api = AnixartApi(SettingsStore.apiBaseUrl(settings.apiEndpoint.first()))
        api.token = token

        val initialized = settings.notificationsInitialized.first()
        val seen = settings.getNotifiedNotificationIds().toMutableSet()
        val toNotify = mutableListOf<Item>()

        fun add(prefix: String, id: Long, releaseId: Int?, title: String, text: String) {
            val key = "$prefix:$id"
            if (seen.contains(key)) return
            seen += key
            if (initialized) {
                toNotify += Item(
                    key = key,
                    notificationId = hashNotificationId(prefix, id),
                    releaseId = releaseId?.takeIf { it > 0 },
                    title = title,
                    text = text,
                )
            }
        }

        suspend fun <T> pages(fetch: suspend (Int) -> PageableResponse<T>): List<T> {
            val all = mutableListOf<T>()
            var page = 1
            while (page <= MAX_PAGES) {
                val res = fetch(page)
                all += res.content
                if (res.content.isEmpty()) break
                if (page >= res.totalPageCount) break
                page++
            }
            return all
        }

        try {
            pages { api.notificationEpisodes(it) }.forEach { n ->
                val ep = n.episode
                val release = ep?.release
                val releaseId = (ep?.releaseId ?: 0L).takeIf { it > 0L }?.toInt()
                    ?: release?.id
                    ?: 0
                val title = release?.titleRu?.ifBlank { release?.title }
                    ?.ifBlank { release?.id?.toString() }
                    ?: "Новая серия"
                val epLabel = ep?.name?.ifBlank { ep?.position?.toString() }.orEmpty().let {
                    if (it.isBlank()) "" else " $it"
                }
                add(
                    PREFIX_EPISODE, n.id, releaseId,
                    title,
                    "Вышла серия$epLabel — смотреть",
                )
            }

            pages { api.notificationRelatedReleases(it) }.forEach { n ->
                val release = n.release
                val title = release?.titleRu?.ifBlank { release?.title }
                    ?.ifBlank { release?.id?.toString() }
                    ?: "Похожий тайтл"
                add(
                    PREFIX_RELATED, n.id, release?.id ?: 0,
                    title,
                    "Вышла новая серия похожего тайтла — смотреть",
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.retry()
        }

        settings.setNotifiedNotificationIds(seen)
        if (!initialized) {
            settings.setNotificationsInitialized(true)
            return Result.success()
        }

        toNotify
            .sortedByDescending { it.notificationId }
            .take(MAX_NOTIFICATIONS_PER_RUN)
            .forEach { item ->
                Notifications.show(
                    applicationContext,
                    item.notificationId,
                    item.title,
                    item.text,
                    item.releaseId,
                )
            }

        return Result.success()
    }

    private fun hashNotificationId(prefix: String, id: Long): Int {
        val base = baseForPrefix(prefix)
        return base + (id % 1_000_000).toInt()
    }

    private fun baseForPrefix(prefix: String): Int = when (prefix) {
        PREFIX_EPISODE -> 1_000_000
        PREFIX_RELATED -> 2_000_000
        else -> 9_000_000
    }

    companion object {
        private const val MAX_PAGES = 3
        private const val MAX_NOTIFICATIONS_PER_RUN = 5

        private const val PREFIX_EPISODE = "ep"
        private const val PREFIX_RELATED = "rr"
    }
}