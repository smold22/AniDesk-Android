package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.notifications.EpisodeNotificationsWorker
import ru.anidesk.app.core.notifications.NotificationHistoryStore
import ru.anidesk.app.core.notifications.StoredNotification
import ru.anidesk.app.core.notifications.timestampSeconds
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.Format
import ru.anidesk.app.ui.components.HeaderIconButton
import ru.anidesk.app.ui.theme.AltBackground
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.ThirdText

private const val NOTIFICATION_PAGES_LIMIT = 20

private enum class NotifFilter(val title: String) {
    ALL("Все"),
    EPISODES("Эпизоды"),
    RELEASES("Релизы"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    api: AnixartApi,
    onOpenRelease: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val historyStore = remember(context) { NotificationHistoryStore(context) }
    val storedNotifications by historyStore.notifications.collectAsState(initial = emptyList())

    var selectedFilter by remember { mutableStateOf(NotifFilter.ALL) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /** [apiPage] — 0-индексированная страница API; публичные методы принимают страницу с 1. */
    suspend fun fetchPage(apiPage: Int): List<StoredNotification> {
        val epResult = runCatching { api.notificationEpisodes(apiPage + 1) }
        val rrResult = runCatching { api.notificationRelatedReleases(apiPage + 1) }
        val epRes = epResult.getOrNull()
        val rrRes = rrResult.getOrNull()
        if (epRes == null && rrRes == null) {
            throw (epResult.exceptionOrNull() ?: rrResult.exceptionOrNull()!!)
        }

        val result = mutableListOf<StoredNotification>()
        epRes?.content?.forEach { n ->
            val ep = n.episode
            val rel = ep?.release
            val title = rel?.titleRu?.ifBlank { rel.title } ?: "Новая серия"
            val releaseId = (ep?.releaseId ?: 0L).takeIf { it > 0L }?.toInt() ?: rel?.id ?: 0
            val epLabel = ep?.name?.ifBlank { ep.position.takeIf { p -> p > 0 }?.toString() }.orEmpty()
            val ts = timestampSeconds(if (n.timestamp > 0) n.timestamp else System.currentTimeMillis())
            result += StoredNotification(
                key = if (n.id > 0) "ep:${n.id}" else "ep:x:$releaseId:$epLabel:${n.timestamp}",
                type = EpisodeNotificationsWorker.TYPE_EPISODE,
                title = title,
                episodeName = epLabel.ifBlank { null },
                releaseId = releaseId,
                image = rel?.image ?: rel?.poster,
                timestamp = ts,
                isNew = n.isNew,
            )
        }
        rrRes?.content?.forEach { n ->
            val rel = n.release
            val releaseId = rel?.id ?: 0
            val title = rel?.titleRu?.ifBlank { rel.title } ?: "Связанный релиз"
            val ts = timestampSeconds(if (n.timestamp > 0) n.timestamp else System.currentTimeMillis())
            val fallbackKey = listOfNotNull(
                releaseId.takeIf { it > 0 }?.toString(),
                n.timestamp.takeIf { it > 0 }?.toString(),
            ).joinToString(":")
            result += StoredNotification(
                key = if (n.id > 0) "rr:${n.id}" else "rr:x:$fallbackKey",
                type = EpisodeNotificationsWorker.TYPE_RELEASE,
                title = title,
                episodeName = null,
                releaseId = releaseId,
                image = rel?.image ?: rel?.poster,
                timestamp = ts,
                isNew = n.isNew,
            )
        }
        return result
    }

    suspend fun syncFromApi() {
        error = null
        page = 0
        endReached = false
        try {
            val toStore = fetchPage(page)
            android.util.Log.i("Pagination", "Notifications refresh page=$page got=${toStore.size}")
            historyStore.upsertAll(toStore)
            if (toStore.isEmpty()) endReached = true
            // Локально считаем прочитанным для бейджа. Серверный readNotifications
            // здесь не вызываем: иначе воркер потеряет isNew и не покажет пуш.
            historyStore.markAllRead()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.networkAwareMessage("Не удалось загрузить:")
        } finally {
            refreshing = false
        }
    }

    suspend fun loadMore() {
        if (endReached || loadingMore || refreshing || page + 1 >= NOTIFICATION_PAGES_LIMIT) return
        loadingMore = true
        try {
            val next = page + 1
            val more = fetchPage(next)
            android.util.Log.i(
                "Pagination",
                "Notifications loadMore page=$next got=${more.size} totalLoaded=${storedNotifications.size}",
            )
            if (more.isEmpty()) {
                endReached = true
            } else {
                historyStore.upsertAll(more)
                page = next
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.networkAwareMessage("Не удалось загрузить:")
        } finally {
            loadingMore = false
        }
    }

    LaunchedEffect(Unit) {
        refreshing = true
        syncFromApi()
    }

    val filteredList = remember(storedNotifications, selectedFilter) {
        when (selectedFilter) {
            NotifFilter.ALL -> storedNotifications
            NotifFilter.EPISODES -> storedNotifications.filter { it.type == EpisodeNotificationsWorker.TYPE_EPISODE }
            NotifFilter.RELEASES -> storedNotifications.filter { it.type == EpisodeNotificationsWorker.TYPE_RELEASE }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(listState, filteredList.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) loadMore()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                onClick = onBack,
            )
            Text(
                text = "Уведомления",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MainText,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            IconButton(
                onClick = {
                    if (!refreshing) {
                        scope.launch {
                            refreshing = true
                            syncFromApi()
                        }
                    }
                },
                enabled = !refreshing,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Обновить",
                    tint = MainText,
                )
            }
            if (storedNotifications.isNotEmpty()) {
                IconButton(onClick = { scope.launch { historyStore.clear() } }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Очистить",
                        tint = MainText,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NotifFilter.entries.forEach { filter ->
                val selected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Carmine.copy(alpha = 0.15f) else AltBackground)
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = filter.title,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Carmine else ThirdText,
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        val content: @Composable () -> Unit = {
            when {
                error != null && storedNotifications.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    ErrorBox(error!!)
                }
                filteredList.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Нет уведомлений",
                        color = ThirdText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 10.dp,
                    ),
                ) {
                    items(filteredList, key = { it.key }) { item ->
                        StoredNotificationCard(
                            item = item,
                            onClick = { if (item.releaseId > 0) onOpenRelease(item.releaseId) },
                        )
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    syncFromApi()
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
    }
}

@Composable
private fun StoredNotificationCard(
    item: StoredNotification,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AltBackground)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!item.image.isNullOrBlank()) {
            AsyncImage(
                model = item.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = ThirdText,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            val text = buildAnnotatedString {
                if (item.type == EpisodeNotificationsWorker.TYPE_EPISODE) {
                    append("Вышла серия ")
                    if (!item.episodeName.isNullOrBlank()) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("(${item.episodeName}) ")
                        }
                    }
                    append("релиза ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("«${item.title}»")
                    }
                } else {
                    append("Появился новый связанный релиз ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("«${item.title}»")
                    }
                }
            }

            Text(
                text = text,
                fontSize = 14.sp,
                color = MainText,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.timestamp > 0) {
                Text(
                    text = Format.getHistoryTime(timestampSeconds(item.timestamp)),
                    fontSize = 12.sp,
                    color = SecondaryText,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}