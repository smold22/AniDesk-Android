package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.Format
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.components.PosterPlaceholder
import ru.anidesk.app.ui.components.ReleaseGrid
import ru.anidesk.app.ui.components.TabHeader
import ru.anidesk.app.ui.components.isTv
import ru.anidesk.app.ui.components.releaseStatusColor
import ru.anidesk.app.ui.theme.AltBackground
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.ThirdText

private const val HISTORY_TAB = 0

private val BOOKMARK_TYPES = listOf(
    1 to "Смотрю",
    2 to "В планах",
    3 to "Просмотрено",
    4 to "Отложено",
    5 to "Брошено",
)

private val BOOKMARK_TABS = listOf("История") + BOOKMARK_TYPES.map { it.second }

@Composable
fun BookmarksScreen(
    api: AnixartApi,
    settingsStore: ru.anidesk.app.core.settings.SettingsStore,
    onOpenRelease: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var releases by remember { mutableStateOf<List<Release>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Release?>(null) }
    var pendingClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val viewTypeState by settingsStore.viewType.collectAsStateWithLifecycle(initialValue = 0)
    val listMode = viewTypeState == 1

    val isHistoryTab = tabIndex == HISTORY_TAB

    suspend fun fetchPage(index: Int, p: Int): List<Release> =
        if (index == HISTORY_TAB) api.history(p).content
        else api.profileList(BOOKMARK_TYPES[index - 1].first, p).content

    LaunchedEffect(tabIndex) {
        loading = true
        error = null
        releases = emptyList()
        endReached = false
        page = 0
        try {
            val res = fetchPage(tabIndex, 0)
            releases = res
            endReached = res.isEmpty()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не удалось загрузить: ${e.message}"
        } finally {
            loading = false
        }
    }

    suspend fun loadMore() {
        if (endReached || loadingMore || releases.isEmpty()) return
        loadingMore = true
        val next = page + 1
        var more: List<Release> = emptyList()
        var ok = false
        repeat(3) { attempt ->
            if (ok) return@repeat
            val res = runCatching { fetchPage(tabIndex, next) }
            res.onSuccess { list ->
                if (list.isNotEmpty()) {
                    more = list
                    ok = true
                }
            }
            res.onFailure {
                android.util.Log.e("Bookmarks", "loadMore page=$next attempt=${attempt + 1} failed: ${it.message}")
            }
            if (!ok) delay(1500)
        }
        android.util.Log.i("Bookmarks", "loadMore tab=$tabIndex page=$next got=${more.size}")
        if (more.isEmpty()) {
            endReached = true
        } else {
            releases = (releases + more).distinctBy { it.id }
            page = next
        }
        loadingMore = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        TabHeader("Закладки", onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BOOKMARK_TABS.forEachIndexed { index, title ->
                val selected = tabIndex == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Carmine.copy(alpha = 0.15f) else AltBackground)
                        .clickable { if (tabIndex != index) tabIndex = index }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Carmine else ThirdText,
                    )
                }
            }
        }
        if (isHistoryTab && releases.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Просмотрено: ${releases.size}",
                    fontSize = 12.sp,
                    color = ThirdText,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { pendingClear = true },
                    enabled = !clearing,
                ) {
                    Text("Очистить все", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        when {
            loading -> LoadingIndicator()
            error != null -> ErrorBox(error!!)
            releases.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isHistoryTab) {
                        "Начни что-нибудь смотреть и это обязательно здесь сохранится"
                    } else {
                        "Список пуст"
                    },
                    color = ThirdText,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            isHistoryTab -> {
                if (isTv()) {
                    ReleaseGrid(
                        releases = releases,
                        onOpenRelease = onOpenRelease,
                        onLoadMore = { loadMore() },
                        onLongClick = { pendingDelete = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(listState, releases.size) {
                        snapshotFlow {
                            val info = listState.layoutInfo
                            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= info.totalItemsCount - 3
                        }.distinctUntilChanged().collect { nearEnd ->
                            if (nearEnd) loadMore()
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(releases.size, key = { releases[it].id }) { index ->
                            val release = releases[index]
                            HistoryListItem(
                                release = release,
                                onClick = { onOpenRelease(release.id) },
                                onDelete = { pendingDelete = release },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        item {
                            if (loadingMore) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LoadingIndicator(modifier = Modifier.size(32.dp))
                                }
                            } else {
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
            else -> {
                ReleaseGrid(
                    releases = releases,
                    onOpenRelease = onOpenRelease,
                    listMode = listMode,
                    loadingMore = loadingMore,
                    onLoadMore = { loadMore() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    pendingDelete?.let { release ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить из истории просмотра") },
            text = { Text(release.titleRu) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            runCatching { api.deleteFromHistory(release.id) }
                            releases = releases.filterNot { it.id == release.id }
                            if (releases.isEmpty()) endReached = true
                        }
                    },
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
    }

    if (pendingClear) {
        AlertDialog(
            onDismissRequest = { pendingClear = false },
            title = { Text("Очистить все") },
            text = { Text("Удалить всю историю просмотра?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingClear = false
                        scope.launch {
                            clearing = true
                            releases.forEach { runCatching { api.deleteFromHistory(it.id) } }
                            releases = emptyList()
                            endReached = true
                            clearing = false
                        }
                    },
                ) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun HistoryListItem(
    release: Release,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onDelete)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(86.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            PosterPlaceholder(
                modifier = Modifier.fillMaxSize(),
            )
            AsyncImage(
                model = release.image,
                contentDescription = release.titleRu,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (release.profileListStatus != null && release.profileListStatus > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(releaseStatusColor(release)),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = release.titleRu.ifEmpty { "Без названия" },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = historyEpisodesString(release),
                    fontSize = 13.sp,
                    color = SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = " • ", fontSize = 13.sp, color = SecondaryText)
                Text(
                    text = String.format("%.1f", release.grade),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText,
                    maxLines = 1,
                )
            }
            val episodeName = release.lastViewEpisodeName.ifEmpty { "неизвестная серия" }
            Text(
                text = if (release.lastViewEpisodeTypeName.isEmpty()) {
                    episodeName
                } else {
                    "$episodeName • ${release.lastViewEpisodeTypeName}"
                },
                fontSize = 12.sp,
                color = SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = if (release.lastViewTimestamp == 0L) "недавно"
                else Format.getHistoryTime(release.lastViewTimestamp),
                fontSize = 12.sp,
                color = ThirdText,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "Удалить из истории просмотра",
            tint = ThirdText,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .clickable(onClick = onDelete)
                .padding(8.dp)
                .size(20.dp),
        )
    }
}

private fun historyEpisodesString(release: Release): String {
    val released = release.episodesReleased ?: 0
    val total = release.episodesTotal
    return when {
        released > 0 && total != null && total > 0 -> "$released из $total эп"
        released > 0 -> "$released из ? эп"
        total != null && total > 0 -> "? из $total эп"
        else -> "? эп"
    }
}