package ru.anidesk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.ui.theme.ThirdText

/** Сетка релизов с адаптивными колонками. */
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
    val gridState = rememberLazyGridState()

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
            columns = GridCells.Adaptive(150.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (header != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { header() }
            }
            itemsIndexed(
                releases,
                key = { _, item -> item.id },
                span = { _, _ -> GridItemSpan(if (listMode) maxLineSpan else 1) },
            ) { index, release ->
                val cardModifier = Modifier
                    .then(
                        if (index == 0 && firstItemFocusRequester != null) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }
                    )
                if (listMode) {
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
                        showInfo = true,
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
    }
}