package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.components.ReleaseCard
import ru.anidesk.app.ui.components.ReleaseListItem
import ru.anidesk.app.ui.theme.AltBackground
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.ThirdText

private sealed interface HomeTab {
    val title: String

    data class Filter(
        override val title: String,
        val sort: Int = 0,
        val statusId: Int? = null,
        val categoryId: Int? = null,
    ) : HomeTab

    data object Schedule : HomeTab {
        override val title = "Расписание"
    }
}

private val TABS = listOf<HomeTab>(
    HomeTab.Filter("Последние", sort = 0),
    HomeTab.Filter("Онгоинги", statusId = 2),
    HomeTab.Filter("Анонсы", statusId = 3),
    HomeTab.Filter("Завершенные", statusId = 1),
    HomeTab.Filter("Фильмы", categoryId = 2),
    HomeTab.Schedule,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    api: AnixartApi,
    settingsStore: ru.anidesk.app.core.settings.SettingsStore,
    onOpenRelease: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNotifications: (() -> Unit)? = null,
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var releases by remember { mutableStateOf<List<Release>>(emptyList()) }
    var schedule by remember { mutableStateOf<List<Pair<String, List<Release>>>>(emptyList()) }
    var scheduleDay by remember { mutableIntStateOf(0) }
    var notificationCount by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var endReached by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    val viewTypeState by settingsStore.viewType.collectAsStateWithLifecycle(initialValue = 0)
    val listMode = viewTypeState == 1
    val scope = rememberCoroutineScope()

    suspend fun loadPage(pageToLoad: Int, replace: Boolean) {
        try {
            val res = when (val tab = TABS[tabIndex]) {
                is HomeTab.Filter -> api.filterReleases(
                    page = pageToLoad,
                    sort = tab.sort,
                    statusId = tab.statusId,
                    categoryId = tab.categoryId,
                )
                HomeTab.Schedule -> return
            }
            releases = if (replace) {
                res.content
            } else {
                (releases + res.content).distinctBy { it.id }
            }
            endReached = res.content.isEmpty()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (replace) error = "Не удалось загрузить: ${e.message}"
        } finally {
            loading = false
            loadingMore = false
        }
    }

    suspend fun refreshCurrentTab() {
        error = null
        val tab = TABS[tabIndex]
        if (tab is HomeTab.Schedule) {
            try {
                val res = api.schedule()
                schedule = listOf(
                    "Понедельник" to res.monday,
                    "Вторник" to res.tuesday,
                    "Среда" to res.wednesday,
                    "Четверг" to res.thursday,
                    "Пятница" to res.friday,
                    "Суббота" to res.saturday,
                    "Воскресенье" to res.sunday,
                )
                scheduleDay = 0
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Не удалось обновить расписание: ${e.message}"
            }
        } else {
            loadPage(0, replace = true)
        }
    }

    LaunchedEffect(tabIndex) {
        loading = true
        error = null
        releases = emptyList()
        page = 0
        endReached = false
        val tab = TABS[tabIndex]
        if (tab is HomeTab.Schedule) {
            try {
                val res = api.schedule()
                schedule = listOf(
                    "Понедельник" to res.monday,
                    "Вторник" to res.tuesday,
                    "Среда" to res.wednesday,
                    "Четверг" to res.thursday,
                    "Пятница" to res.friday,
                    "Суббота" to res.saturday,
                    "Воскресенье" to res.sunday,
                )
                scheduleDay = 0
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Не удалось загрузить расписание: ${e.message}"
            } finally {
                loading = false
            }
        } else {
            loadPage(0, replace = true)
        }
    }

    LaunchedEffect(Unit) {
        if (api.token != null) {
            runCatching { notificationCount = api.notificationCount().count }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        HomeHeader(
            notificationCount = notificationCount,
            onOpenSearch = onOpenSearch,
            onOpenNotifications = onOpenNotifications,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TABS.forEachIndexed { index, tab ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (tabIndex == index) Carmine.copy(alpha = 0.15f) else AltBackground)
                        .clickable { if (tabIndex != index) tabIndex = index }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = tab.title,
                        fontSize = 14.sp,
                        fontWeight = if (tabIndex == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (tabIndex == index) Carmine else ThirdText,
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    refreshCurrentTab()
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                loading -> LoadingIndicator()
                error != null -> ErrorBox(error!!)
                TABS[tabIndex] is HomeTab.Schedule ->
                    ScheduleTab(schedule, scheduleDay, onSelectDay = { scheduleDay = it }, onOpenRelease = onOpenRelease)
                else -> {
                    val gridState = rememberLazyGridState()
                    LaunchedEffect(gridState, releases.size) {
                        snapshotFlow {
                            val info = gridState.layoutInfo
                            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= info.totalItemsCount - 3
                        }.collect { nearEnd ->
                            if (nearEnd && !endReached && !loadingMore && releases.isNotEmpty()) {
                                loadingMore = true
                                page++
                                loadPage(page, replace = false)
                            }
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            releases,
                            key = { it.id },
                            span = { GridItemSpan(if (listMode) maxLineSpan else 1) },
                        ) { release ->
                            if (listMode) {
                                ReleaseListItem(release = release, onClick = { onOpenRelease(release.id) })
                            } else {
                                ReleaseCard(release = release, onClick = { onOpenRelease(release.id) })
                            }
                        }
                        if (loadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                    Text("Загрузка...", color = ThirdText, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    notificationCount: Int,
    onOpenSearch: () -> Unit,
    onOpenNotifications: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AltBackground)
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Поиск",
                tint = ThirdText,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Поиск",
                fontSize = 16.sp,
                color = ThirdText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        if (onOpenNotifications != null) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenNotifications),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Уведомления",
                    tint = MainText,
                    modifier = Modifier.size(22.dp),
                )
                if (notificationCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Carmine),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (notificationCount > 9) "9+" else notificationCount.toString(),
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleTab(
    schedule: List<Pair<String, List<Release>>>,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    onOpenRelease: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            schedule.forEachIndexed { index, (day, _) ->
                val selected = selectedDay == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Carmine else AltBackground)
                        .clickable { onSelectDay(index) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = day,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) androidx.compose.ui.graphics.Color.White else ThirdText,
                    )
                }
            }
        }
        val releases = schedule.getOrNull(selectedDay)?.second ?: emptyList()
        if (releases.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Нет релизов", color = ThirdText)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(releases, key = { it.id }) { release ->
                    ReleaseCard(release = release, onClick = { onOpenRelease(release.id) })
                }
            }
        }
    }
}
