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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.components.ReleaseCard
import ru.anidesk.app.ui.components.ReleaseListItem
import ru.anidesk.app.ui.components.TabHeader
import ru.anidesk.app.ui.theme.AltBackground
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.ThirdText

private val BOOKMARK_TYPES = listOf(
    1 to "Смотрю",
    2 to "В планах",
    3 to "Просмотрено",
    4 to "Отложено",
    5 to "Брошено",
)

@Composable
fun BookmarksScreen(
    api: AnixartApi,
    settingsStore: ru.anidesk.app.core.settings.SettingsStore,
    onOpenRelease: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var typeIndex by remember { mutableIntStateOf(0) }
    var releases by remember { mutableStateOf<List<Release>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val viewTypeState by settingsStore.viewType.collectAsStateWithLifecycle(initialValue = 0)
    val listMode = viewTypeState == 1

    LaunchedEffect(typeIndex) {
        loading = true
        error = null
        releases = emptyList()
        endReached = false
        page = 0
        try {
            val res = api.profileList(BOOKMARK_TYPES[typeIndex].first, 0)
            releases = res.content
            endReached = res.content.isEmpty()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не удалось загрузить: ${e.message}"
        } finally {
            loading = false
        }
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
            BOOKMARK_TYPES.forEachIndexed { index, (_, title) ->
                val selected = typeIndex == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Carmine else AltBackground)
                        .clickable { if (typeIndex != index) typeIndex = index }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) Color.White else ThirdText,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        when {
            loading -> LoadingIndicator()
            error != null -> ErrorBox(error!!)
            releases.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Список пуст", color = ThirdText, fontSize = 14.sp)
            }
            else -> {
                val gridState = rememberLazyGridState()
                LaunchedEffect(gridState, releases.size) {
                    snapshotFlow {
                        val info = gridState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible >= info.totalItemsCount - 3
                    }.distinctUntilChanged().collect { nearEnd ->
                        if (nearEnd && !endReached && !loadingMore && releases.isNotEmpty()) {
                            loadingMore = true
                            val next = page + 1
                            var more: List<Release> = emptyList()
                            var ok = false
                            repeat(3) { attempt ->
                                if (ok) return@repeat
                                val res = runCatching { api.profileList(BOOKMARK_TYPES[typeIndex].first, next).content }
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
                            android.util.Log.i("Bookmarks", "loadMore type=${BOOKMARK_TYPES[typeIndex].first} page=$next got=${more.size}")
                            if (more.isEmpty()) {
                                endReached = true
                            } else {
                                releases = (releases + more).distinctBy { it.id }
                                page = next
                            }
                            loadingMore = false
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
