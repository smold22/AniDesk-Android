package ru.anidesk.app.core.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.EpisodeNotification
import ru.anidesk.app.core.network.PageableResponse
import ru.anidesk.app.core.network.RelatedReleaseNotification
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.core.settings.SettingsStore

/**
 * Фоновая проверка уведомлений:
 * - сохраняет фиды уведомлений (серии/связанные релизы) в локальную историю для вкладки;
 * - определяет новые серии отслеживаемых тайтлов по закладкам (episodes_released + базовая линия);
 * - показывает системные уведомления о новых элементах и открывает релиз по нажатию.
 */
class EpisodeNotificationsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private data class PushItem(
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

        val historyStore = NotificationHistoryStore(applicationContext)
        val seen = settings.getNotifiedNotificationIds().toMutableSet()
        val toStore = mutableListOf<StoredNotification>()
        val toPush = mutableListOf<PushItem>()
        val pendingPushKeys = mutableSetOf<String>()

        fun queuePush(
            key: String,
            notificationId: Int,
            timestamp: Long,
            releaseId: Int?,
            title: String,
            text: String,
        ) {
            if (seen.contains(key) || pendingPushKeys.contains(key)) return
            pendingPushKeys += key
            toPush += PushItem(key, notificationId, timestamp, releaseId, title, text)
        }

        suspend fun <T> pages(fetch: suspend (Int) -> PageableResponse<T>): List<T> {
            val all = mutableListOf<T>()
            var page = 1
            while (page <= MAX_PAGES) {
                val res = try {
                    fetch(page)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch notification page $page", e)
                    break
                }
                all += res.content
                if (res.content.isEmpty() || page >= res.totalPageCount) break
                page++
            }
            return all
        }

        fun releaseTitle(release: Release?): String =
            release?.titleRu?.ifBlank { release.title }?.ifBlank { release.id.toString() }
                ?: "Новый релиз"

        // 1) Серверные уведомления о сериях -> история вкладки + пуш для новых.
        runCatching {
            val episodes = pages<EpisodeNotification> { api.notificationEpisodes(it) }
            episodes.forEach { n ->
                val ep = n.episode
                val rel = ep?.release
                val releaseId = (ep?.releaseId ?: 0L).takeIf { it > 0L }?.toInt() ?: rel?.id ?: 0
                val title = releaseTitle(rel).let { if (it == "Новый релиз") "Новая серия" else it }
                val epLabel = ep?.name?.ifBlank { ep.position.takeIf { p -> p > 0 }?.toString() }.orEmpty()
                val key = notificationKey(PREFIX_SERVER_EPISODE, n.id, "${releaseId}:${epLabel}:${n.timestamp}")
                val stored = StoredNotification(
                    key = key,
                    type = TYPE_EPISODE,
                    title = title,
                    episodeName = epLabel.ifBlank { null },
                    releaseId = releaseId,
                    image = rel?.image ?: rel?.poster,
                    timestamp = if (n.timestamp > 0) n.timestamp else System.currentTimeMillis(),
                    isNew = n.isNew,
                )
                toStore += stored
                if (n.isNew) {
                    queuePush(
                        key = key,
                        notificationId = hashNotificationId(PREFIX_SERVER_EPISODE, n.id, "${releaseId}:${epLabel}"),
                        timestamp = stored.timestamp,
                        releaseId = releaseId.takeIf { it > 0 },
                        title = title,
                        text = if (epLabel.isNotBlank()) "Вышла серия ($epLabel)" else "Вышла новая серия",
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Episode notifications feed failed", it) }

        // 2) Новые серии по закладкам (локальное определение) -> история + пуш.
        var bookmarksFetched = false
        val currentCounts = mutableMapOf<Int, Int>()
        val currentReleases = mutableMapOf<Int, Release>()
        runCatching {
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
        }.onFailure { Log.w(TAG, "Bookmarks fetch failed", it) }

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
                        val key = notificationKey(PREFIX_LOCAL_EPISODE, 0L, "$releaseId:$position")
                        val ts = now + position
                        toStore += StoredNotification(
                            key = key,
                            type = TYPE_EPISODE,
                            title = title,
                            episodeName = position.toString(),
                            releaseId = releaseId,
                            image = release?.image ?: release?.poster,
                            timestamp = ts,
                            isNew = true,
                        )
                        queuePush(
                            key = key,
                            notificationId = hashNotificationId(PREFIX_LOCAL_EPISODE, 0L, "$releaseId:$position"),
                            timestamp = ts,
                            releaseId = releaseId,
                            title = title,
                            text = "Вышла серия $position",
                        )
                    }
                }
            }
            settings.setEpisodeBaseline(currentCounts)
            settings.setEpisodeBaselineInitialized(true)
        }

        // 3) Связанные релизы -> история + пуш для новых.
        runCatching {
            val items = pages<RelatedReleaseNotification> { api.notificationRelatedReleases(it) }
            items.forEach { n ->
                val release = n.release
                val releaseId = release?.id ?: 0
                val title = releaseTitle(release)
                val fallbackKey = listOfNotNull(
                    releaseId.takeIf { it > 0 }?.toString(),
                    n.timestamp.takeIf { it > 0 }?.toString(),
                ).joinToString(":")
                val key = notificationKey(PREFIX_RELATED, n.id, fallbackKey)
                val ts = if (n.timestamp > 0) n.timestamp else System.currentTimeMillis()
                toStore += StoredNotification(
                    key = key,
                    type = TYPE_RELEASE,
                    title = if (title == "Новый релиз") "Связанный релиз" else title,
                    episodeName = null,
                    releaseId = releaseId,
                    image = release?.image ?: release?.poster,
                    timestamp = ts,
                    isNew = n.isNew,
                )
                if (n.isNew) {
                    queuePush(
                        key = key,
                        notificationId = hashNotificationId(PREFIX_RELATED, n.id, fallbackKey),
                        timestamp = ts,
                        releaseId = releaseId.takeIf { it > 0 },
                        title = if (title == "Новый релиз") "Связанный релиз" else title,
                        text = "Появился новый связанный релиз",
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Related release notifications feed failed", it) }

        // Сохраняем историю (даже без пушей), чтобы вкладка обновлялась в фоне.
        runCatching { historyStore.upsertAll(toStore) }

        if (!bookmarksFetched && toStore.isEmpty()) return Result.retry()

        val shown = toPush
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

        // Отметим прочитанными на сервере, чтобы не разрастался "new", но локальная история останется.
        runCatching { api.readNotifications() }

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
        PREFIX_SERVER_EPISODE -> 1_000_000
        PREFIX_LOCAL_EPISODE -> 3_000_000
        PREFIX_RELATED -> 2_000_000
        else -> 9_000_000
    }

    companion object {
        private const val MAX_PAGES = 3
        private const val MAX_BOOKMARK_PAGES = 40
        private val BOOKMARK_STATUSES = 1..5

        private const val TAG = "EpisodeNotifications"
        private const val PREFIX_SERVER_EPISODE = "ep"
        private const val PREFIX_LOCAL_EPISODE = "ep-local"
        private const val PREFIX_RELATED = "rr"

        const val TYPE_EPISODE = "episode"
        const val TYPE_RELEASE = "release"
    }
}
