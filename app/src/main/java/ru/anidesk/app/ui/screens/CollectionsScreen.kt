package ru.anidesk.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Profile
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.core.network.UserCollection
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.components.ReleaseGrid
import ru.anidesk.app.ui.components.TabHeader
import ru.anidesk.app.ui.theme.AltBackground
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.ThirdText

/**
 * Вкладка «Подборки» — публичный каталог подборок.
 * Тап по подборке открывает её релизы.
 */
@Composable
fun CollectionsScreen(
    api: AnixartApi,
    sessionStore: SessionStore,
    onOpenRelease: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var collections by remember { mutableStateOf<List<UserCollection>>(emptyList()) }
    var selected by remember { mutableStateOf<UserCollection?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    // Детальный вид подборки
    var releases by remember { mutableStateOf<List<Release>>(emptyList()) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailEndReached by remember { mutableStateOf(false) }
    var detailPage by remember { mutableIntStateOf(0) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<UserCollection?>(null) }
    var myId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun fetchCollections(p: Int): List<UserCollection> {
        return runCatching { api.collections(p).content }.getOrElse { emptyList() }
    }

    LaunchedEffect(Unit) {
        myId = sessionStore.profileId.first()
        loading = true
        error = null
        try {
            collections = fetchCollections(0)
            endReached = collections.isEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не удалось загрузить подборки: ${e.message}"
        } finally {
            loading = false
        }
    }

    suspend fun loadMoreCollections() {
        if (endReached || loadingMore || collections.isEmpty()) return
        loadingMore = true
        val more = fetchCollections(page + 1)
        if (more.isEmpty()) {
            endReached = true
        } else {
            collections = (collections + more).distinctBy { it.id }
            page += 1
        }
        loadingMore = false
    }

    LaunchedEffect(selected) {
        val col = selected ?: return@LaunchedEffect
        detailLoading = true
        detailError = null
        try {
            val res = api.collectionReleases(col.id, 0)
            releases = res.content
            detailPage = 0
            detailEndReached = res.content.isEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            detailError = "Не удалось загрузить тайтлы: ${e.message}"
            detailLoading = false
            return@LaunchedEffect
        }
        myId = sessionStore.profileId.first()
        detail = runCatching { api.collection(col.id) }.getOrNull() ?: col
        detailLoading = false
    }

    suspend fun loadMoreReleases() {
        val col = selected ?: return
        if (detailEndReached || detailLoading || releases.isEmpty()) return
        val more = runCatching { api.collectionReleases(col.id, detailPage + 1).content }
            .getOrElse { emptyList() }
        if (more.isEmpty()) {
            detailEndReached = true
        } else {
            releases = (releases + more).distinctBy { it.id }
            detailPage += 1
        }
    }

    BackHandler {
        if (selected != null) selected = null else onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        val current = selected
        if (current != null) {
            TabHeader(title = "Подборки", onBack = { selected = null })
            var showAddReleaseDialog by remember { mutableStateOf(false) }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
            when {
                detailError != null -> ErrorBox(detailError!!)
                detailLoading && releases.isEmpty() -> LoadingIndicator()
                releases.isEmpty() -> EmptyCollectionsState("В подборке пока нет тайтлов")
                else -> ReleaseGrid(
                    releases = releases,
                    onOpenRelease = onOpenRelease,
                    modifier = Modifier.fillMaxSize(),
                    onLoadMore = { loadMoreReleases() },
                    header = {
                        val detailOwner = detail?.creator?.id ?: detail?.author?.id
                        CollectionDetailHeader(
                            collection = current,
                            creator = detail?.creator ?: detail?.author,
                            myId = myId,
                            onAddRelease = if (detailOwner != null && detailOwner == myId) {
                                { showAddReleaseDialog = true }
                            } else {
                                null
                            },
                        )
                    },
                )
            }
            if (showAddReleaseDialog) {
                AddReleaseDialog(
                    api = api,
                    onDismiss = { showAddReleaseDialog = false },
                    onAdd = { release ->
                        scope.launch {
                            runCatching { api.collectionReleaseAdd(current.id, release.id) }
                            showAddReleaseDialog = false
                            if (releases.none { it.id == release.id }) {
                                releases = listOf(release) + releases
                            }
                        }
                    },
                )
            }
        } else {
            TabHeader(title = "Подборки", onBack = onBack)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
            when {
                loading -> LoadingIndicator()
                error != null -> ErrorBox(error!!)
                collections.isEmpty() -> EmptyCollectionsState("Пока нет подборок")
                else -> CollectionsList(
                    collections = collections,
                    onOpen = { selected = it },
                    onLoadMore = { loadMoreCollections() },
                    loadingMore = loadingMore,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CollectionsList(
    collections: List<UserCollection>,
    onOpen: (UserCollection) -> Unit,
    onLoadMore: suspend () -> Unit,
    loadingMore: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, collections.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd) onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(collections, key = { it.id }) { collection ->
            CollectionListCard(
                collection = collection,
                onClick = { onOpen(collection) },
            )
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

@Composable
private fun CollectionListCard(
    collection: UserCollection,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AltBackground)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = collection.title.ifEmpty { "Подборка" },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MainText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = collection.image.ifEmpty { null },
                contentDescription = null,
                modifier = Modifier
                    .size(width = 120.dp, height = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (collection.description.isNotBlank()) {
                    Text(
                        text = collection.description,
                        fontSize = 12.sp,
                        color = SecondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${collectionTitleCount(collection)} • ${collection.favoritesCount} в избранном • ${collection.commentCount} комментариев",
                    fontSize = 11.sp,
                    color = ThirdText,
                )
            }
        }
    }
}

@Composable
private fun CollectionDetailHeader(
    collection: UserCollection,
    creator: Profile?,
    myId: Int?,
    onAddRelease: (() -> Unit)? = null,
) {
    var descriptionExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = collection.title.ifEmpty { "Подборка" },
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MainText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onAddRelease != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAddRelease,
                modifier = Modifier.height(34.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Carmine),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Добавить тайтл", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = collection.image.ifEmpty { null },
                contentDescription = null,
                modifier = Modifier
                    .size(width = 160.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AltBackground),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${collectionTitleCount(collection)} тайтлов",
                    fontSize = 12.sp,
                    color = ThirdText,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${collection.favoritesCount} в избранном",
                    fontSize = 12.sp,
                    color = ThirdText,
                )
            }
        }
        if (collection.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = collection.description,
                fontSize = 13.sp,
                color = SecondaryText,
                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (descriptionExpanded) "Свернуть" else "Ещё",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Carmine,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { descriptionExpanded = !descriptionExpanded }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        if (creator != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AltBackground)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = creator.avatar.ifEmpty { null },
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Автор коллекции",
                        fontSize = 11.sp,
                        color = ThirdText,
                    )
                    Text(
                        text = creator.login.ifEmpty { "Пользователь" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MainText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (creator.id == myId) {
                    Text(
                        text = "Это вы",
                        fontSize = 13.sp,
                        color = ThirdText,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

private fun collectionTitleCount(collection: UserCollection): Int =
    if (collection.releases.isNotEmpty()) collection.releases.size else collection.countReleases

@Composable
private fun EmptyCollectionsState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = ThirdText, fontSize = 15.sp)
    }
}

@Composable
private fun AddReleaseDialog(
    api: AnixartApi,
    onDismiss: () -> Unit,
    onAdd: (release: Release) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Release>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        searching = true
        searchError = null
        try {
            results = api.searchReleases(0, q)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            searchError = "Не удалось найти тайтлы: ${e.message}"
        } finally {
            searching = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Добавить тайтл", fontWeight = FontWeight.Bold, color = MainText)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Название тайтла") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { if (!searching) scope.launch { doSearch() } }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Найти",
                                tint = Carmine,
                            )
                        }
                    },
                    keyboardActions = KeyboardActions(onSearch = { if (!searching) scope.launch { doSearch() } }),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                when {
                    searching -> LoadingIndicator()
                    searchError != null -> Text(searchError!!, color = Carmine, fontSize = 13.sp)
                    results.isEmpty() && query.isNotBlank() ->
                        Text("Ничего не найдено", color = ThirdText, fontSize = 13.sp)

                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(results, key = { it.id }) { release ->
                            ReleaseSearchRow(
                                release = release,
                                onClick = { onAdd(release) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = ThirdText)
            }
        },
    )
}

@Composable
private fun ReleaseSearchRow(
    release: Release,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AltBackground)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = if (release.poster.isNotEmpty()) release.poster else release.image.ifEmpty { null },
            contentDescription = null,
            modifier = Modifier
                .size(width = 120.dp, height = 68.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(release.titleRu.ifEmpty { release.titleOriginal.ifEmpty { "Тайтл" } })
                    if (release.year.isNotEmpty()) append(" (${release.year})")
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MainText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}