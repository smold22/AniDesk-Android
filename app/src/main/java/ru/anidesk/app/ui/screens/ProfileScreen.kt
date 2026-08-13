package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.components.ReleaseCard
import ru.anidesk.app.ui.theme.AltBackground
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.ThirdText

@Composable
fun ProfileScreen(
    api: AnixartApi,
    sessionStore: SessionStore,
    onOpenRelease: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var profileId by remember { mutableStateOf<Int?>(null) }
    var login by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<Release>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<Release>>(emptyList()) }
    var historyPage by remember { mutableIntStateOf(0) }
    var favoritesPage by remember { mutableIntStateOf(0) }
    var historyEnded by remember { mutableStateOf(false) }
    var favoritesEnded by remember { mutableStateOf(false) }
    var historyLoading by remember { mutableStateOf(false) }
    var favoritesLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val id = sessionStore.profileId.first()
            profileId = id
            if (id != null) {
                val profile = api.profile(id).profile
                login = profile?.login ?: ""
                avatar = profile?.avatar ?: ""
            }
            history = api.history(0).content
            favorites = api.favorites(0).content
            historyEnded = history.isEmpty()
            favoritesEnded = favorites.isEmpty()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не удалось загрузить профиль: ${e.message}"
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
        when {
            loading -> LoadingIndicator()
            error != null -> ErrorBox(error!!)
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = avatar.ifEmpty { null },
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AltBackground),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = login.ifEmpty { "Гость" },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MainText,
                            )
                            Text(
                                text = "ID: ${profileId ?: "-"}",
                                fontSize = 13.sp,
                                color = ThirdText,
                            )
                        }
                        Button(
                            onClick = {
                                scope.launch { sessionStore.clear() }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AltBackground,
                                contentColor = Carmine,
                            ),
                        ) {
                            Text("Выйти", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (history.isNotEmpty()) {
                    item { SectionLabel("Продолжить просмотр") }
                    item {
                        PagedReleaseRow(
                            releases = history,
                            onOpenRelease = onOpenRelease,
                            onLoadMore = {
                                loadMore(
                                    current = history,
                                    currentPage = historyPage,
                                    ended = historyEnded,
                                    isLoading = historyLoading,
                                    fetch = { api.history(it).content },
                                    onLoading = { historyLoading = it },
                                    onSuccess = { list, page, ended ->
                                        history = list
                                        historyPage = page
                                        historyEnded = ended
                                    },
                                )
                            },
                        )
                    }
                }

                if (favorites.isNotEmpty()) {
                    item { SectionLabel("Избранное") }
                    item {
                        PagedReleaseRow(
                            releases = favorites,
                            onOpenRelease = onOpenRelease,
                            onLoadMore = {
                                loadMore(
                                    current = favorites,
                                    currentPage = favoritesPage,
                                    ended = favoritesEnded,
                                    isLoading = favoritesLoading,
                                    fetch = { api.favorites(it).content },
                                    onLoading = { favoritesLoading = it },
                                    onSuccess = { list, page, ended ->
                                        favorites = list
                                        favoritesPage = page
                                        favoritesEnded = ended
                                    },
                                )
                            },
                        )
                    }
                }

                if (history.isEmpty() && favorites.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Text("Пока пусто", color = ThirdText, fontSize = 14.sp)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
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
private fun SectionLabel(title: String) {
    Text(
        text = title,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = MainText,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
    )
}
