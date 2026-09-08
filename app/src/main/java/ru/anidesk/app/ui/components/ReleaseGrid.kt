package ru.anidesk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.ui.theme.ThirdText

/** Описание релиза в формате AniTV: «статус • жанры • категория • Серии: X/Y». */
fun releaseGridDescription(release: Release): String {
    val statusText = release.status?.name.orEmpty()
    val genreText = release.genres.orEmpty()
    val categoryText = release.category?.name.orEmpty()
    val total = release.episodesTotal
    val released = release.episodesReleased
    val seriesText = if (total != null && total > 0) {
        "Серии: ${released ?: 0}/$total"
    } else {
        null
    }
    return listOfNotNull(statusText, genreText, categoryText, seriesText).joinToString(" • ")
}

/**
 * Сетка релизов в стиле AniTV: на TV — 6 колонок и нижняя плашка
 * с названием/описанием выбранного релиза.
 */
@Composable
fun ReleaseGrid(
    releases: List<Release>,
    onOpenRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: (suspend () -> Unit)? = null,
    listMode: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
    loadingMore: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    onLongClick: ((Release) -> Unit)? = null,
) {
    val tv = isTv()
    var selected by remember { mutableStateOf<Release?>(null) }
    var focusedIndex by rememberSaveable { mutableStateOf(-1) }
    val restoreFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        if (tv && focusedIndex >= 0) {
            delay(150)
            repeat(3) {
                runCatching { restoreFocusRequester.requestFocus() }
                delay(100)
            }
        }
    }

    LaunchedEffect(releases.size) {
        if (releases.isEmpty()) {
            selected = null
            focusedIndex = -1
        }
    }

    LaunchedEffect(gridState, releases.size) {
        if (onLoadMore == null) return@LaunchedEffect
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) onLoadMore()
        }
    }

    Box(modifier = modifier) {
        LazyVerticalGrid(
            columns = if (tv) GridCells.Fixed(6) else GridCells.Adaptive(150.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = if (tv) 14.dp else 8.dp,
                top = if (tv) 10.dp else 8.dp,
                end = if (tv) 14.dp else 8.dp,
                bottom = if (tv) 96.dp else 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (tv) 12.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (tv) 12.dp else 8.dp),
        ) {
            if (header != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { header() }
            }
            itemsIndexed(
                releases,
                key = { _, item -> item.id },
                span = { _, _ -> GridItemSpan(if (!tv && listMode) maxLineSpan else 1) },
            ) { index, release ->
                val cardModifier = Modifier
                    .then(
                        when {
                            index == 0 && firstItemFocusRequester != null ->
                                Modifier.focusRequester(firstItemFocusRequester)
                            tv && index == focusedIndex && focusedIndex >= 0 ->
                                Modifier.focusRequester(restoreFocusRequester)
                            else -> Modifier
                        }
                    )
                    .onFocusChanged {
                        if (it.isFocused) {
                            focusedIndex = index
                            selected = release
                        }
                    }
                if (!tv && listMode) {
                    ReleaseListItem(
                        release = release,
                        onClick = { onOpenRelease(release.id) },
                        modifier = cardModifier,
                    )
                } else {
                    ReleaseCard(
                        release = release,
                        onClick = { onOpenRelease(release.id) },
                        modifier = cardModifier,
                        showInfo = !tv,
                        onLongClick = onLongClick?.let { { it(release) } },
                    )
                }
            }
            if (loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        Text("Загрузка...", color = ThirdText, fontSize = 12.sp)
                    }
                }
            }
            if (footer != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { footer() }
            }
        }
        if (tv && selected != null) {
            GridDescriptionBar(selected!!, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun GridDescriptionBar(release: Release, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0x90000000)),
                )
            )
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(min = 480.dp)) {
            Text(
                text = release.titleRu,
                fontSize = 20.sp,
                color = Color(0xFFEEEEEE),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black, Offset.Zero, 4f)),
            )
            Text(
                text = releaseGridDescription(release),
                fontSize = 16.sp,
                color = Color(0xFFB2B2B2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black, Offset.Zero, 4f)),
            )
        }
    }
}