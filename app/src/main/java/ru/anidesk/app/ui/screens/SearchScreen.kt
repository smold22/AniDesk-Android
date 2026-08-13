package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@Composable
fun SearchScreen(
    api: AnixartApi,
    settingsStore: ru.anidesk.app.core.settings.SettingsStore,
    onBack: () -> Unit,
    onOpenRelease: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Release>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val viewTypeState by settingsStore.viewType.collectAsStateWithLifecycle(initialValue = 0)
    val listMode = viewTypeState == 1

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            searched = false
            loading = false
            endReached = true
            return@LaunchedEffect
        }
        loading = true
        endReached = false
        page = 0
        delay(400)
        try {
            results = api.searchReleases(0, q)
            searched = true
            endReached = results.isEmpty()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Ошибка поиска: ${e.message}"
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = MainText,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Поиск релизов", color = ThirdText) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Carmine,
                    unfocusedBorderColor = AltBackground,
                    cursorColor = Carmine,
                    focusedContainerColor = AltBackground,
                    unfocusedContainerColor = AltBackground,
                ),
            )
        }

        when {
            loading -> LoadingIndicator()
            error != null -> ErrorBox(error!!)
            query.trim().length < 2 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Введите минимум 2 символа", color = ThirdText, fontSize = 14.sp)
            }
            results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (searched) "Ничего не найдено" else "", color = ThirdText, fontSize = 14.sp)
            }
            else -> {
                val gridState = rememberLazyGridState()
                LaunchedEffect(gridState, results.size) {
                    snapshotFlow {
                        val info = gridState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible >= info.totalItemsCount - 3
                    }.distinctUntilChanged().collect { nearEnd ->
                        if (nearEnd && !endReached && !loadingMore && results.isNotEmpty()) {
                            loadingMore = true
                            val next = page + 1
                            runCatching { api.searchReleases(next, query.trim()) }
                                .onSuccess { more ->
                                    if (more.isEmpty()) {
                                        endReached = true
                                    } else {
                                        results = (results + more).distinctBy { it.id }
                                        page = next
                                    }
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
                        results,
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
