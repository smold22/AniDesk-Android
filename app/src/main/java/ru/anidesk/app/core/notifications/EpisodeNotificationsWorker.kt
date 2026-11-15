package ru.anidesk.app.core.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.PageableResponse
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.core.network.RelatedReleaseNotification
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.core.settings.SettingsStore

/**
 * Фоновая проверка уведомлений через WorkManager:
 *   - закладки профиля (статусы 1..5) — новые серии у отслеживаемых тайтлов;
 *   - notification/related/release — новые серии похожих тайтлов.
 *
 * Новые серии определяются локально по [Release.episodesReleased] и базовой линии
 * из [SettingsStore]. При первом запуске базовая линия только запоминается, чтобы
 * не присылать уведомления за все уже вышедшие серии.
 */
class EpisodeNotificationsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private data class Item(
        val key: String,
        val notificationId: Int,
        val timestamp: Long,
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

        runCatching { NotificationPreferences.ensureConfigured(api, settings) }

        val seen = settings.getNotifiedNotificationIds().toMutableSet()
        val pending = mutableSetOf<String>()
        val toNotify = mutableListOf<Item>()
        var bookmarksFetched = false

        fun add(
            prefix: String,
            id: Long,
            fallbackKey: String,
            timestamp: Long,
            releaseId: Int?,
            title: String,
            text: String,
        ) {
            val key = notificationKey(prefix, id, fallbackKey)
            if (seen.contains(key) || pending.contains(key)) return
            pending += key
            toNotify += Item(
                key = key,
                notificationId = hashNotificationId(prefix, id, fallbackKey),
                timestamp = timestamp,
                releaseId = releaseId?.takeIf { it > 0 },
                title = title,
                text = text,
            )
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

        suspend fun runFeed(block: suspend () -> Unit) {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Notification feed failed", e)
            }
        }

        fun releaseTitle(release: Release?): String =
            release?.titleRu?.ifBlank { release.title }?.ifBlank { release.id.toString() }
                ?: "Новый релиз"

        // Новые серии у тайтлов из закладок.
        val currentCounts = mutableMapOf<Int, Int>()
        val currentReleases = mutableMapOf<Int, Release>()
        runFeed {
            for (status in BOOKMARK_STATUSES) {
                var page = 0
                while (page < MAX_BOOKMARK_PAGES) {
                    val res = api.profileList(status, page)
                    res.content.forEach { release ->
                        val released = release.episodesReleased ?: return@forEach
                        currentCounts[release.id] = maxOf(currentCounts[release.id] ?: 0, released)
                        currentReleases[release.id] = release
                    }
                    if (res.content.isEmpty()) break
                    page++
                }
            }
            bookmarksFetched = true
        }

        if (bookmarksFetched) {
            val baseline = settings.episodeBaseline.first()
            val baselineInitialized = settings.getEpisodeBaselineInitialized()
            if (baselineInitialized) {
                val now = System.currentTimeMillis()
                currentCounts.forEach { (releaseId, current) ->
                    val previous = baseline[releaseId] ?: return@forEach
                    if (current <= previous) return@forEach
                    val release = currentReleases[releaseId]
                    val title = releaseTitle(release)
                    for (position in (previous + 1)..current) {
                        add(
                            PREFIX_LOCAL_EPISODE,
                            0L,
                            "$releaseId:$position",
                            now + position,
                            releaseId,
                            title,
                            "Вышла серия $position",
                        )
                    }
                }
            }
            settings.setEpisodeBaseline(currentCounts)
            settings.setEpisodeBaselineInitialized(true)
        }

        // Похожие релизы.
        runFeed {
            val items = pages<RelatedReleaseNotification> { api.notificationRelatedReleases(it) }
            items.forEach { n ->
                val release = n.release
                val fallbackKey = listOfNotNull(
                    release?.id?.takeIf { it > 0 }?.toString(),
                    n.timestamp.takeIf { it > 0 }?.toString(),
                ).joinToString(":")
                add(
                    PREFIX_RELATED, n.id, fallbackKey, n.timestamp, release?.id ?: 0,
                    releaseTitle(release),
                    "Появился новый связанный релиз",
                )
            }
        }

        if (!bookmarksFetched) return Result.retry()

        val shown = toNotify
            .sortedByDescending { it.timestamp }
            .filter { item ->
                Notifications.show(
                    applicationContext,
                    item.notificationId,
                    item.title,
                    item.text,
                    item.releaseId,
                )
            }

        if (shown.isNotEmpty()) {
            seen += shown.map { it.key }
            settings.setNotifiedNotificationIds(seen)
        }
        settings.setNotificationsInitialized(true)

        return Result.success()
    }

    private fun notificationKey(prefix: String, id: Long, fallbackKey: String): String =
        if (id > 0) "$prefix:$id" else "$prefix:x:$fallbackKey"

    private fun hashNotificationId(prefix: String, id: Long, fallbackKey: String): Int {
        val base = baseForPrefix(prefix)
        val value = if (id > 0) id else (fallbackKey.hashCode().toLong() and 0xffffffffL)
        return base + (value % 1_000_000).toInt()
    }

    private fun baseForPrefix(prefix: String): Int = when (prefix) {
        PREFIX_LOCAL_EPISODE -> 3_000_000
        PREFIX_RELATED -> 2_000_000
        else -> 9_000_000
    }

    companion object {
        private const val MAX_PAGES = 3
        private const val MAX_BOOKMARK_PAGES = 40
        private val BOOKMARK_STATUSES = 1..5

        private const val TAG = "EpisodeNotifications"
        private const val PREFIX_LOCAL_EPISODE = "ep-local"
        private const val PREFIX_RELATED = "rr"
    }
}
