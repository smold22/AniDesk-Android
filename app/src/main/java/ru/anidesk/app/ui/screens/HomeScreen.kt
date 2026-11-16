package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.components.ReleaseGrid
import ru.anidesk.app.ui.theme.AltBackground
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.ThirdText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    api: AnixartApi,
    settingsStore: ru.anidesk.app.core.settings.SettingsStore,
    onOpenRelease: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNotifications: (() -> Unit)? = null,
) {
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var notificationCount by remember { mutableIntStateOf(0) }
    val viewTypeState by settingsStore.viewType.collectAsStateWithLifecycle(initialValue = 0)
    val listMode = viewTypeState == 1
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(api.token) {
        if (api.token != null) {
            scope.launch {
                runCatching { notificationCount = api.notificationCount().count }
            }
        }
        onPauseOrDispose { }
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
            HOME_TABS.forEachIndexed { index, tab ->
                val selected = state.tabIndex == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Carmine.copy(alpha = 0.15f) else AltBackground)
                        .clickable { viewModel.selectTab(index) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = tab.title,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Carmine else ThirdText,
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        val content: @Composable () -> Unit = {
            when {
                state.loading -> LoadingIndicator()
                state.error != null -> ErrorBox(state.error!!)
                HOME_TABS.getOrNull(state.tabIndex) is HomeTab.Schedule ->
                    ScheduleTab(
                        state.schedule,
                        state.scheduleDay,
                        onSelectDay = { viewModel.selectDay(it) },
                        onOpenRelease = onOpenRelease,
                        listMode = listMode,
                    )
                else -> {
                    ReleaseGrid(
                        releases = state.releases,
                        onOpenRelease = onOpenRelease,
                        listMode = listMode,
                        loadingMore = state.loadingMore,
                        onLoadMore = { viewModel.loadNextPage() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
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
                .clip(RoundedCornerShape(8.dp))
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
                    .clip(RoundedCornerShape(8.dp))
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
                            .clip(RoundedCornerShape(8.dp))
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
    listMode: Boolean = false,
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Carmine.copy(alpha = 0.15f) else AltBackground)
                        .clickable { onSelectDay(index) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = day,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Carmine else ThirdText,
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
            ReleaseGrid(
                releases = releases,
                onOpenRelease = onOpenRelease,
                listMode = listMode,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
