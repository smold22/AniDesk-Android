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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.components.ReleaseGrid
import ru.anidesk.app.ui.components.TvKeyRouter
import ru.anidesk.app.ui.components.isTv
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
    var filter by remember { mutableStateOf(SearchFilter()) }
    var showFilter by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Release>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val viewTypeState by settingsStore.viewType.collectAsStateWithLifecycle(initialValue = 0)
    val listMode = viewTypeState == 1
    val tv = isTv()
    val searchFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    var searchFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (tv) {
            delay(150)
            searchFocusRequester.requestFocus()
        }
    }

    if (tv) {
        DisposableEffect(Unit) {
            TvKeyRouter.fieldDpadDown = {
                if (searchFocused && results.isNotEmpty()) {
                    runCatching { firstResultFocusRequester.requestFocus() }
                    true
                } else {
                    false
                }
            }
            onDispose {
                TvKeyRouter.fieldDpadDown = null
            }
        }
    }

    LaunchedEffect(query, filter) {
        val q = query.trim()
        val filterActive = !filter.isEmpty
        if (q.length < 2 && !filterActive) {
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
            results = if (q.isNotEmpty()) {
                api.searchReleases(0, q)
            } else {
                api.filterReleases(
                    page = 0,
                    sort = filter.sort,
                    statusId = filter.statusId,
                    categoryId = filter.categoryId,
                    country = filter.country,
                    startYear = filter.startYear,
                    endYear = filter.endYear,
                    season = filter.season,
                    genres = filter.genres,
                    types = filter.types,
                    ageRatings = filter.ageRatings,
                ).content
            }
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

    if (showFilter) {
        SearchFilterScreen(
            api = api,
            initial = filter,
            onApply = {
                filter = it
                showFilter = false
            },
            onClose = { showFilter = false },
        )
        return
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
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AltBackground)
                    .then(if (tv) Modifier.focusRequester(searchFocusRequester) else Modifier)
                    .onFocusChanged { searchFocused = it.isFocused }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = MainText, fontSize = 16.sp),
                    cursorBrush = SolidColor(Carmine),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Поиск релизов",
                                    color = ThirdText,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Фильтр",
                tint = if (!filter.isEmpty) Carmine else MainText,
                modifier = Modifier
                    .clickable { showFilter = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
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
                ReleaseGrid(
                    releases = results,
                    onOpenRelease = onOpenRelease,
                    listMode = listMode,
                    loadingMore = loadingMore,
                    firstItemFocusRequester = firstResultFocusRequester,
                    onLoadMore = {
                        if (!endReached && !loadingMore && results.isNotEmpty()) {
                            loadingMore = true
                            val next = page + 1
                            runCatching {
                                if (query.trim().isNotEmpty()) {
                                    api.searchReleases(next, query.trim())
                                } else {
                                    api.filterReleases(
                                        page = next,
                                        sort = filter.sort,
                                        statusId = filter.statusId,
                                        categoryId = filter.categoryId,
                                        country = filter.country,
                                        startYear = filter.startYear,
                                        endYear = filter.endYear,
                                        season = filter.season,
                                        genres = filter.genres,
                                        types = filter.types,
                                        ageRatings = filter.ageRatings,
                                    ).content
                                }
                            }
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
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
