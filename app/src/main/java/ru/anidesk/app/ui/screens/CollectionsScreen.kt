package ru.anidesk.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Profile
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.core.network.UserCollection
import ru.anidesk.app.core.settings.SettingsStore
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
    settingsStore: SettingsStore,
    onOpenRelease: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val viewType by settingsStore.viewType.collectAsStateWithLifecycle(initialValue = 0)
    val listMode = viewType == 1
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
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    var myCollections by remember { mutableStateOf<List<UserCollection>>(emptyList()) }
    var myLoading by remember { mutableStateOf(false) }
    var myLoadingMore by remember { mutableStateOf(false) }
    var myEndReached by remember { mutableStateOf(false) }
    var myPage by remember { mutableIntStateOf(0) }
    var myError by remember { mutableStateOf<String?>(null) }
    var myRefreshKey by remember { mutableIntStateOf(0) }

    var editorOpen by remember { mutableStateOf(false) }
    var editorCollection by remember { mutableStateOf<UserCollection?>(null) }
    var editorReleases by remember { mutableStateOf<List<Release>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

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
            error = e.networkAwareMessage("Не удалось загрузить подборки:")
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

    LaunchedEffect(tabIndex, myId, myRefreshKey) {
        if (tabIndex != 1) return@LaunchedEffect
        val id = myId ?: sessionStore.profileId.first()?.also { myId = it }
        if (id == null) {
            myCollections = emptyList()
            myError = "Войдите в аккаунт, чтобы видеть свои подборки"
            myLoading = false
            return@LaunchedEffect
        }
        myLoading = true
        myError = null
        myEndReached = false
        try {
            val res = api.profileCollections(id, 0)
            myCollections = res.content
            myPage = 0
            myEndReached = res.content.isEmpty() || res.content.size >= res.totalCount
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            myError = e.networkAwareMessage("Не удалось загрузить мои подборки:")
        } finally {
            myLoading = false
        }
    }

    suspend fun loadMoreMyCollections() {
        val id = myId ?: return
        if (myEndReached || myLoadingMore || myCollections.isEmpty()) return
        myLoadingMore = true
        val more = runCatching { api.profileCollections(id, myPage + 1).content }
            .getOrElse { emptyList() }
        if (more.isEmpty()) {
            myEndReached = true
        } else {
            myCollections = (myCollections + more).distinctBy { it.id }
            myPage += 1
        }
        myLoadingMore = false
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
            detailError = e.networkAwareMessage("Не удалось загрузить тайтлы:")
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

    BackHandler(enabled = !editorOpen) {
        if (selected != null) selected = null else onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        if (editorOpen) {
            CollectionEditorScreen(
                api = api,
                existing = editorCollection,
                initialReleases = editorReleases,
                applyWindowInsets = false,
                onCancel = { editorOpen = false },
                onSaved = { saved, savedReleases ->
                    editorOpen = false
                    myRefreshKey += 1
                    tabIndex = 1
                    myCollections = if (myCollections.any { it.id == saved.id }) {
                        myCollections.map {
                            if (it.id == saved.id) {
                                saved.copy(releaseCount = savedReleases.size)
                            } else {
                                it
                            }
                        }
                    } else {
                        listOf(saved.copy(releaseCount = savedReleases.size)) + myCollections
                    }
                    selected = saved
                    detail = saved.copy(releaseCount = savedReleases.size)
                    releases = savedReleases
                    detailPage = 0
                    detailEndReached = savedReleases.isEmpty()
                    detailError = null
                },
            )
        } else {
            val current = selected
            if (current != null) {
                TabHeader(title = "Подборки", onBack = { selected = null })
                var showAddReleaseDialog by remember { mutableStateOf(false) }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
                val shown = detail ?: current
                val detailOwner = shown.creator?.id ?: shown.author?.id
                val isOwner = detailOwner != null && detailOwner == myId
                when {
                    detailError != null -> ErrorBox(detailError!!)
                    detailLoading && releases.isEmpty() -> LoadingIndicator()
                    releases.isEmpty() -> Box(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            CollectionDetailHeader(
                                collection = shown,
                                creator = shown.creator ?: shown.author,
                                myId = myId,
                                coverUrl = shown.displayCoverUrl(),
                                onAddRelease = if (isOwner) {
                                    { showAddReleaseDialog = true }
                                } else {
                                    null
                                },
                                onEdit = if (isOwner) {
                                    {
                                        editorCollection = shown
                                        editorReleases = releases
                                        editorOpen = true
                                    }
                                } else {
                                    null
                                },
                                onDelete = if (isOwner) {
                                    {
                                        deleteError = null
                                        showDeleteConfirm = true
                                    }
                                } else {
                                    null
                                },
                            )
                            Text(
                                text = "В подборке пока нет тайтлов",
                                color = ThirdText,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 40.dp),
                            )
                        }
                    }

                    else -> ReleaseGrid(
                        releases = releases,
                        onOpenRelease = onOpenRelease,
                        listMode = listMode,
                        modifier = Modifier.fillMaxSize(),
                        onLoadMore = { loadMoreReleases() },
                        header = {
                            CollectionDetailHeader(
                                collection = shown,
                                creator = shown.creator ?: shown.author,
                                myId = myId,
                                coverUrl = shown.displayCoverUrl(releases),
                                onAddRelease = if (isOwner) {
                                    { showAddReleaseDialog = true }
                                } else {
                                    null
                                },
                                onEdit = if (isOwner) {
                                    {
                                        editorCollection = shown
                                        editorReleases = releases
                                        editorOpen = true
                                    }
                                } else {
                                    null
                                },
                                onDelete = if (isOwner) {
                                    {
                                        deleteError = null
                                        showDeleteConfirm = true
                                    }
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
                                val result = runCatching { api.collectionReleaseAdd(current.id, release.id) }
                                showAddReleaseDialog = false
                                if (result.getOrNull()?.code == 0 && releases.none { it.id == release.id }) {
                                    releases = listOf(release) + releases
                                    val count = releases.size
                                    detail = shown.copy(releaseCount = count)
                                    myCollections = myCollections.map {
                                        if (it.id == shown.id) it.copy(releaseCount = count) else it
                                    }
                                }
                            }
                        },
                    )
                }
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Удалить подборку?", fontWeight = FontWeight.Bold, color = MainText) },
                        text = {
                            Text(
                                text = deleteError ?: "Подборка будет удалена без возможности восстановления.",
                                color = if (deleteError != null) Carmine else SecondaryText,
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val result = runCatching { api.deleteCollection(current.id) }
                                        if (result.getOrNull()?.code == 0) {
                                            showDeleteConfirm = false
                                            selected = null
                                            tabIndex = 1
                                            myRefreshKey += 1
                                            myCollections = myCollections.filterNot { it.id == current.id }
                                        } else {
                                            deleteError = "Не удалось удалить (код ${result.getOrNull()?.code ?: "?"})"
                                        }
                                    }
                                },
                            ) {
                                Text("Удалить", color = Carmine)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text("Отмена", color = ThirdText)
                            }
                        },
                    )
                }
            } else {
                TabHeader(title = "Подборки", onBack = onBack)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CollectionTabChip(
                        title = "Все подборки",
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                    )
                    CollectionTabChip(
                        title = "Мои подборки",
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
                if (tabIndex == 0) {
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
                } else {
                    when {
                        myLoading -> LoadingIndicator()
                        myError != null -> ErrorBox(myError!!)
                        myCollections.isEmpty() -> MyCollectionsEmptyState(
                            onCreate = {
                                editorCollection = null
                                editorReleases = emptyList()
                                editorOpen = true
                            },
                        )

                        else -> CollectionsList(
                            collections = myCollections,
                            onOpen = { selected = it },
                            onLoadMore = { loadMoreMyCollections() },
                            loadingMore = myLoadingMore,
                            modifier = Modifier.fillMaxSize(),
                            header = {
                                OutlinedButton(
                                    onClick = {
                                        editorCollection = null
                                        editorReleases = emptyList()
                                        editorOpen = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Carmine),
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Создать подборку", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            },
                        )
                    }
                }
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
    header: (@Composable () -> Unit)? = null,
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
        if (header != null) {
            item { header() }
        }
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
    val coverUrl = collection.displayCoverUrl()
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
            if (collection.isPrivate) {
                Text(
                    text = "Приватная",
                    fontSize = 11.sp,
                    color = ThirdText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 120.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Без обложки", fontSize = 11.sp, color = ThirdText)
                }
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionDetailHeader(
    collection: UserCollection,
    creator: Profile?,
    myId: Int?,
    coverUrl: String? = null,
    onAddRelease: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var descriptionExpanded by remember { mutableStateOf(false) }
    val cover = coverUrl ?: collection.displayCoverUrl()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = collection.title.ifEmpty { "Подборка" },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MainText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (collection.isPrivate) {
                Text(
                    text = "Приватная",
                    fontSize = 11.sp,
                    color = ThirdText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (onAddRelease != null || onEdit != null || onDelete != null) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onAddRelease != null) {
                    OutlinedButton(
                        onClick = onAddRelease,
                        modifier = Modifier.height(34.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Carmine),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Добавить тайтл", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (onEdit != null) {
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) {
                        Text("Изменить", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Carmine)
                    }
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) {
                        Text("Удалить", fontSize = 13.sp, color = ThirdText)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 160.dp, height = 90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AltBackground),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 160.dp, height = 90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AltBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Без обложки", fontSize = 11.sp, color = ThirdText)
                }
            }
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
    maxOf(collection.releaseCount, collection.releases.size)

internal fun Release.coverImageUrl(): String? =
    poster.ifBlank { image }.takeIf { it.isNotBlank() }

internal fun UserCollection.displayCoverUrl(fallbackReleases: List<Release> = emptyList()): String? =
    sequenceOf(
        image,
        releases.firstOrNull()?.coverImageUrl().orEmpty(),
        releasesPreview.firstOrNull()?.coverImageUrl().orEmpty(),
        fallbackReleases.firstOrNull()?.coverImageUrl().orEmpty(),
    ).firstOrNull { it.isNotBlank() }

@Composable
private fun CollectionTabChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Carmine.copy(alpha = 0.15f) else AltBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Carmine else ThirdText,
        )
    }
}

@Composable
private fun EmptyCollectionsState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = ThirdText, fontSize = 15.sp)
    }
}

@Composable
private fun MyCollectionsEmptyState(onCreate: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "У вас пока нет подборок", color = ThirdText, fontSize = 15.sp)
            OutlinedButton(
                onClick = onCreate,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Carmine),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Создать подборку", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun AddReleaseDialog(
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
            searchError = e.networkAwareMessage("Не удалось найти тайтлы:")
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