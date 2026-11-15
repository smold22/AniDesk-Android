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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.Format
import ru.anidesk.app.ui.components.HeaderIconButton
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.theme.AltBackground
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.ThirdText

sealed interface AnixNotification {
    val id: Long
    val timestamp: Long
    val isNew: Boolean

    data class Episode(
        override val id: Long,
        override val timestamp: Long,
        override val isNew: Boolean,
        val releaseId: Int,
        val title: String,
        val episodeName: String,
        val image: String?,
    ) : AnixNotification

    data class RelatedRelease(
        override val id: Long,
        override val timestamp: Long,
        override val isNew: Boolean,
        val releaseId: Int,
        val title: String,
        val image: String?,
    ) : AnixNotification
}

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
    var selectedFilter by remember { mutableStateOf(NotifFilter.ALL) }
    var allNotifications by remember { mutableStateOf<List<AnixNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        error = null
        try {
            coroutineScope {
                suspend fun <T> fetch(block: suspend () -> List<T>): List<T> =
                    runCatching { block() }.getOrDefault(emptyList())

                val epDeferred = async { fetch { api.notificationEpisodes(1).content } }
                val rrDeferred = async { fetch { api.notificationRelatedReleases(1).content } }

                val epList = epDeferred.await()
                val rrList = rrDeferred.await()

                val result = mutableListOf<AnixNotification>()

                epList.forEach { n ->
                    val ep = n.episode
                    val rel = ep?.release
                    val title = rel?.titleRu?.ifBlank { rel.title } ?: "Новая серия"
                    val releaseId = (ep?.releaseId ?: 0L).takeIf { it > 0L }?.toInt() ?: rel?.id ?: 0
                    val epLabel = ep?.name?.ifBlank { ep.position.toString() }.orEmpty()
                    result += AnixNotification.Episode(
                        id = n.id,
                        timestamp = n.timestamp,
                        isNew = n.isNew,
                        releaseId = releaseId,
                        title = title,
                        episodeName = epLabel,
                        image = rel?.image ?: rel?.poster,
                    )
                }

                rrList.forEach { n ->
                    val rel = n.release
                    val title = rel?.titleRu?.ifBlank { rel.title } ?: "Связанный релиз"
                    result += AnixNotification.RelatedRelease(
                        id = n.id,
                        timestamp = n.timestamp,
                        isNew = n.isNew,
                        releaseId = rel?.id ?: 0,
                        title = title,
                        image = rel?.image ?: rel?.poster,
                    )
                }

                allNotifications = result.sortedByDescending { it.timestamp }
                runCatching { api.readNotifications() }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.networkAwareMessage("Не удалось загрузить:")
        } finally {
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { load() }

    fun clearAll() {
        scope.launch {
            loading = true
            try {
                api.removeAllNotifications()
                allNotifications = emptyList()
            } catch (e: Exception) {
                error = e.networkAwareMessage("Не удалось очистить уведомления:")
            } finally {
                loading = false
            }
        }
    }

    val filteredList = remember(allNotifications, selectedFilter) {
        when (selectedFilter) {
            NotifFilter.ALL -> allNotifications
            NotifFilter.EPISODES -> allNotifications.filterIsInstance<AnixNotification.Episode>()
            NotifFilter.RELEASES -> allNotifications.filterIsInstance<AnixNotification.RelatedRelease>()
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
                    if (!loading) {
                        scope.launch {
                            refreshing = true
                            load()
                        }
                    }
                },
                enabled = !loading,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Обновить",
                    tint = MainText,
                )
            }
            if (allNotifications.isNotEmpty()) {
                IconButton(onClick = { clearAll() }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Удалить все",
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
                loading -> LoadingIndicator()
                error != null -> Box(
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
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 10.dp,
                    ),
                ) {
                    items(filteredList, key = { "${it::class.simpleName}_${it.id}" }) { item ->
                        AnixartNotificationCard(
                            item = item,
                            onClick = {
                                when (item) {
                                    is AnixNotification.Episode -> if (item.releaseId > 0) onOpenRelease(item.releaseId)
                                    is AnixNotification.RelatedRelease -> if (item.releaseId > 0) onOpenRelease(item.releaseId)
                                }
                            },
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
                    load()
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
    }
}

@Composable
private fun AnixartNotificationCard(
    item: AnixNotification,
    onClick: () -> Unit,
) {
    val imageUrl = when (item) {
        is AnixNotification.Episode -> item.image
        is AnixNotification.RelatedRelease -> item.image
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AltBackground)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
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
                when (item) {
                    is AnixNotification.Episode -> {
                        append("Вышла серия ")
                        if (item.episodeName.isNotBlank()) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("(${item.episodeName}) ")
                            }
                        }
                        append("релиза ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("«${item.title}»")
                        }
                    }
                    is AnixNotification.RelatedRelease -> {
                        append("Появился новый связанный релиз ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("«${item.title}»")
                        }
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
                    text = Format.getHistoryTime(item.timestamp),
                    fontSize = 12.sp,
                    color = SecondaryText,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}