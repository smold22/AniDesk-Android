package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.components.ReleaseCard
import ru.anidesk.app.ui.components.TabHeader
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText

@Composable
fun DiscoverScreen(
    api: AnixartApi,
    onOpenRelease: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var watching by remember { mutableStateOf<List<Release>>(emptyList()) }
    var recommendations by remember { mutableStateOf<List<Release>>(emptyList()) }
    var watchingPage by remember { mutableIntStateOf(0) }
    var recommendationsPage by remember { mutableIntStateOf(0) }
    var watchingEnded by remember { mutableStateOf(false) }
    var recommendationsEnded by remember { mutableStateOf(false) }
    var watchingLoading by remember { mutableStateOf(false) }
    var recommendationsLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            watching = api.discoverWatching(0).content
            recommendations = api.discoverRecommendations(0).content
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не удалось загрузить: ${e.message}"
        } finally {
            loading = false
        }
    }

    suspend fun loadMore(
        current: List<Release>,
        currentPage: Int,
        ended: Boolean,
        isLoading: Boolean,
        fetch: suspend (Int) -> List<Release>,
        onLoading: (Boolean) -> Unit,
        onSuccess: (List<Release>, Int, Boolean) -> Unit,
    ) {
        if (ended || current.isEmpty() || isLoading) return
        onLoading(true)
        val next = currentPage + 1
        val more = runCatching { fetch(next) }.getOrNull()
        if (more != null) {
            onSuccess((current + more).distinctBy { it.id }, next, more.isEmpty())
        }
        onLoading(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        TabHeader("Смотрят", onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
        when {
            loading -> LoadingIndicator()
            error != null -> ErrorBox(error!!)
            else -> {
                val gridState = rememberLazyGridState()
                LaunchedEffect(gridState) {
                    snapshotFlow {
                        val info = gridState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible >= info.totalItemsCount - 3
                    }.distinctUntilChanged().collect { nearEnd ->
                        if (nearEnd) {
                            loadMore(
                                current = watching,
                                currentPage = watchingPage,
                                ended = watchingEnded,
                                isLoading = watchingLoading,
                                fetch = { api.discoverWatching(it).content },
                                onLoading = { watchingLoading = it },
                                onSuccess = { list, page, ended ->
                                    watching = list
                                    watchingPage = page
                                    watchingEnded = ended
                                },
                            )
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
                    if (watching.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionTitle("Смотрят сейчас")
                        }
                        items(watching, key = { it.id }) { release ->
                            ReleaseCard(release = release, onClick = { onOpenRelease(release.id) })
                        }
                    }
                    if (recommendations.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionTitle("Рекомендации")
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            PagedReleaseRow(
                                releases = recommendations,
                                onOpenRelease = onOpenRelease,
                                onLoadMore = {
                                    loadMore(
                                        current = recommendations,
                                        currentPage = recommendationsPage,
                                        ended = recommendationsEnded,
                                        isLoading = recommendationsLoading,
                                        fetch = { api.discoverRecommendations(it).content },
                                        onLoading = { recommendationsLoading = it },
                                        onSuccess = { list, page, ended ->
                                            recommendations = list
                                            recommendationsPage = page
                                            recommendationsEnded = ended
                                        },
                                    )
                                },
                            )
                        }
                    }
                    if (watching.isEmpty() && recommendations.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(48.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) {
                                Text("Пока пусто", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagedReleaseRow(
    releases: List<Release>,
    onOpenRelease: (Int) -> Unit,
    onLoadMore: suspend () -> Unit,
) {
    val rowState = rememberLazyListState()
    LaunchedEffect(rowState) {
        snapshotFlow {
            val info = rowState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 2
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) onLoadMore()
        }
    }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(releases, key = { it.id }) { release ->
            ReleaseCard(
                release = release,
                onClick = { onOpenRelease(release.id) },
                modifier = Modifier.width(110.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = MainText,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
    )
}
