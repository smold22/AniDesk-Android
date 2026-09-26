package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ClipEntry
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Dubber
import ru.anidesk.app.core.network.Episode
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.core.network.Source
import ru.anidesk.app.core.settings.SettingsStore
import ru.anidesk.app.downloads.EpisodeDownloader
import ru.anidesk.app.player.PlayerQuality
import ru.anidesk.app.player.SourceParsers
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.Format
import ru.anidesk.app.ui.components.HeaderIconButton
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.components.PosterPlaceholder
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.PlayerRed
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.SelectButton
import ru.anidesk.app.ui.theme.ThirdText

/** Загрузка доступна только для источников, которые реально поддерживаются. */
private fun isDownloadableSource(sourceName: String): Boolean {
    val lower = sourceName.lowercase()
    return lower.contains("kodik") || lower.contains("cvh") || lower.contains("sibnet")
}

private val BOOKMARK_TYPES = listOf(
    0 to "Без закладки",
    1 to "Смотрю",
    2 to "В планах",
    3 to "Просмотрено",
    4 to "Отложено",
    5 to "Брошено",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseScreen(
    api: AnixartApi,
    settingsStore: SettingsStore,
    releaseId: Int,
    onBack: () -> Unit,
    onOpenRelease: (Int) -> Unit,
    onPlay: (releaseId: Int, dubberId: Int, sourceId: Int, position: Int, sourceName: String) -> Unit,
) {
    var release by remember { mutableStateOf<Release?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showEpisodePicker by rememberSaveable { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    var bookmarkType by remember { mutableIntStateOf(0) }
    var showBookmarkMenu by remember { mutableStateOf(false) }
    var relatedReleases by remember { mutableStateOf<List<Release>>(emptyList()) }
    var relatedPage by remember { mutableIntStateOf(0) }
    var relatedHasMore by remember { mutableStateOf(false) }
    var relatedLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(releaseId) {
        loading = true
        error = null
        try {
            val res = api.releaseInfo(releaseId)
            if (res.release == null) {
                error = "Релиз не найден"
            } else {
                release = res.release
                isFavorite = res.release.isFavorite
                bookmarkType = res.release.profileListStatus ?: 0
                runCatching {
                    val rel = res.release.related
                    if (res.release.relatedCount > 0 && rel != null) {
                        val relatedRes = api.relatedReleases(rel.id, 1)
                        relatedReleases = relatedRes.content
                        relatedPage = relatedRes.currentPage
                        relatedHasMore = relatedRes.currentPage + 1 < relatedRes.totalPageCount
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не удалось загрузить релиз: ${e.message}"
        } finally {
            loading = false
        }
    }

    when {
        loading -> LoadingIndicator()
        error != null -> ErrorBox(error!!)
        release != null -> {
            val r = release!!
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                item {
                    ReleaseHeader(r, onBack = onBack)
                }
                item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { showEpisodePicker = true },
                                enabled = (r.episodesReleased ?: 0) > 0 && !r.isViewBlocked,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PlayerRed),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                            ) {
                                Text("Смотреть", fontWeight = FontWeight.SemiBold)
                            }
                            Box {
                                Button(
                                    onClick = { showBookmarkMenu = true },
                                    modifier = Modifier.height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SelectButton,
                                        contentColor = MainText,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                ) {
                                    Text("В закладки", fontWeight = FontWeight.SemiBold)
                                }
                                DropdownMenu(
                                    expanded = showBookmarkMenu,
                                    onDismissRequest = { showBookmarkMenu = false },
                                ) {
                                    BOOKMARK_TYPES.forEach { (type, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                showBookmarkMenu = false
                                                if (type == bookmarkType) return@DropdownMenuItem
                                                scope.launch {
                                                    if (bookmarkType != 0) {
                                                        runCatching { api.removeFromProfileList(r.id, bookmarkType) }
                                                    }
                                                    if (type != 0) {
                                                        runCatching { api.addToProfileList(r.id, type) }
                                                    }
                                                    bookmarkType = type
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        if (isFavorite) {
                                            runCatching { api.removeFavorite(r.id) }
                                            isFavorite = false
                                        } else {
                                            runCatching { api.addFavorite(r.id) }
                                            isFavorite = true
                                        }
                                    }
                                },
                                modifier = Modifier.height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SelectButton,
                                    contentColor = MainText,
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = if (isFavorite) "Убрать из избранного" else "В избранное",
                                        tint = if (isFavorite) Carmine else MainText,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(r.favoritesCount.toString())
                                }
                            }
                        }
                    }
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = r.description,
                                color = MainText,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                            )
                        }
                    }
                    item {
                        ReleaseInfoGrid(r)
                    }
                releaseRowsItems(
                    r = r,
                    relatedReleases = relatedReleases,
                    relatedHasMore = relatedHasMore,
                    relatedLoading = relatedLoading,
                    onLoadMoreRelated = {
                        if (!relatedLoading) {
                            val rel = release?.related
                            if (rel != null) {
                                relatedLoading = true
                                scope.launch {
                                    runCatching {
                                        api.relatedReleases(rel.id, relatedPage + 2)
                                    }.onSuccess { res ->
                                        relatedReleases = relatedReleases + res.content
                                        relatedPage = res.currentPage
                                        relatedHasMore = res.currentPage + 1 < res.totalPageCount
                                    }
                                    relatedLoading = false
                                }
                            }
                        }
                    },
                    onOpenRelease = onOpenRelease,
                )
            }
            }
        }
    }

    if (showEpisodePicker && release != null) {
        EpisodePickerSheet(
            api = api,
            settingsStore = settingsStore,
            release = release!!,
            onDismiss = { showEpisodePicker = false },
            onPlay = onPlay,
        )
    }
}

@Composable
private fun ReleaseHeader(r: Release, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var titleExpanded by remember { mutableStateOf(false) }

    Column {
        HeaderIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Назад",
            onClick = onBack,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(185.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            PosterPlaceholder(modifier = Modifier.fillMaxSize())
            AsyncImage(
                model = r.image,
                contentDescription = r.titleRu,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                r.titleRu,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MainText,
                lineHeight = 24.sp,
                maxLines = if (titleExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { titleExpanded = !titleExpanded },
                            onLongPress = {
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("title", r.titleRu)))
                                    Toast.makeText(context, "Название скопировано", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    },
            )
            Spacer(Modifier.height(4.dp))
            if (r.titleOriginal.isNotEmpty()) {
                Text(
                    r.titleOriginal,
                    fontSize = 14.sp,
                    color = SecondaryText,
                    maxLines = if (titleExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { titleExpanded = !titleExpanded },
                                onLongPress = {
                                    scope.launch {
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("title_original", r.titleOriginal)))
                                        Toast.makeText(context, "Оригинальное название скопировано", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Format.getAgeRate(r.ageRating),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MainText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(r.status?.name ?: "", fontSize = 13.sp, color = ThirdText)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    String.format("%.2f", r.grade),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MainText,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "• ${r.voteCount} голосов",
                    fontSize = 13.sp,
                    color = SecondaryText,
                )
            }
            Text(
                "Серий: ${Format.getEpisodeString(r)}",
                fontSize = 13.sp,
                color = SecondaryText,
            )
        }
        }
    }
}

private fun LazyListScope.releaseRowsItems(
    r: Release,
    relatedReleases: List<Release>,
    relatedHasMore: Boolean,
    relatedLoading: Boolean,
    onLoadMoreRelated: () -> Unit,
    onOpenRelease: (Int) -> Unit,
) {
    if (r.screenshotImages.isNotEmpty()) {
        item {
            Text(
                "Кадры",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MainText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(r.screenshotImages) { img ->
                    var focused by remember { mutableStateOf(false) }
                    AsyncImage(
                        model = img,
                        contentDescription = null,
                        modifier = Modifier
                            .width(220.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (focused) 3.dp else 0.dp,
                                color = if (focused) Carmine else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .focusable()
                            .onFocusChanged { focused = it.isFocused },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
    if (relatedReleases.isNotEmpty()) {
        item {
            Text(
                "Связанные релизы",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MainText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(relatedReleases) { related ->
                    Column(
                        modifier = Modifier
                            .width(120.dp)
                            .clickable { onOpenRelease(related.id) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            PosterPlaceholder(
                                modifier = Modifier.fillMaxSize(),
                            )
                            AsyncImage(
                                model = related.image.ifBlank { related.poster },
                                contentDescription = related.titleRu,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Text(
                            related.titleRu,
                            fontSize = 12.sp,
                            color = MainText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (relatedHasMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(SelectButton)
                                .clickable(onClick = onLoadMoreRelated),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (relatedLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp,
                                    color = MainText,
                                )
                            } else {
                                Text(
                                    "Ещё",
                                    color = MainText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    item { Spacer(Modifier.height(24.dp)) }
}

@Composable
private fun ReleaseInfoGrid(r: Release) {
    val rows = listOfNotNull(
        "Год" to (r.year.ifEmpty { null }),
        "Страна" to (r.country.ifEmpty { null }),
        "Жанры" to (r.genres.ifEmpty { null }),
        "Студия" to (r.studio.ifEmpty { null }),
        "Режиссёр" to (r.director.ifEmpty { null }),
        "Перевод" to (r.translators.ifEmpty { null }),
        "Категория" to r.category?.name,
    ).filter { it.second != null }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        rows.forEach { (label, value) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(label, fontSize = 13.sp, color = ThirdText, modifier = Modifier.width(90.dp))
                Text(value!!, fontSize = 13.sp, color = MainText, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodePickerSheet(
    api: AnixartApi,
    settingsStore: SettingsStore,
    release: Release,
    onDismiss: () -> Unit,
    onPlay: (Int, Int, Int, Int, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var dubbers by remember { mutableStateOf<List<Dubber>>(emptyList()) }
    var sources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var selectedDubber by remember { mutableStateOf<Int?>(null) }
    var selectedSource by remember { mutableStateOf<Int?>(null) }
    var loadingEpisodes by remember { mutableStateOf(true) }

    suspend fun loadEpisodes() {
        val dubberId = selectedDubber ?: return
        val sourceId = selectedSource ?: return
        loadingEpisodes = true
        try {
            episodes = api.getEpisodes(release.id, dubberId, sourceId).episodes
        } finally {
            loadingEpisodes = false
        }
    }

    LaunchedEffect(Unit) {
        try {
            val dubbersRes = api.getDubbers(release.id)
            dubbers = dubbersRes.types
            if (dubbers.isNotEmpty()) {
                val saved = settingsStore.getDubberSource(release.id)
                selectedDubber = saved.first
                    ?.let { d -> dubbers.firstOrNull { it.id == d } }
                    ?.id
                    ?: dubbers.first().id
                val sourcesRes = api.getDubberSources(release.id, selectedDubber!!)
                sources = sourcesRes.sources
                if (sources.isNotEmpty()) {
                    selectedSource = saved.second
                        ?.let { s -> sources.firstOrNull { it.id == s } }
                        ?.id
                        ?: sources.first().id
                    loadEpisodes()
                }
            }
        } finally {
            loadingEpisodes = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.statusBarsPadding(),
        sheetState = sheetState,
        sheetGesturesEnabled = true,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 4.dp)
                    .width(52.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ThirdText),
                )
            }
        },
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        EpisodePickerContent(
            api = api,
            settingsStore = settingsStore,
            release = release,
            dubbers = dubbers,
            sources = sources,
            episodes = episodes,
            selectedDubber = selectedDubber,
            selectedSource = selectedSource,
            loadingEpisodes = loadingEpisodes,
            onDubberSelect = { d ->
                selectedDubber = d.id
                selectedSource = null
                sources = emptyList()
                episodes = emptyList()
                scope.launch {
                    val res = api.getDubberSources(release.id, d.id)
                    sources = res.sources
                    val src = res.sources.firstOrNull()?.id
                    selectedSource = src
                    if (src != null) {
                        settingsStore.setDubberSource(release.id, d.id, src)
                    }
                    loadEpisodes()
                }
            },
            onSourceSelect = { s ->
                selectedSource = s.id
                scope.launch {
                    loadEpisodes()
                    selectedDubber?.let {
                        settingsStore.setDubberSource(release.id, it, s.id)
                    }
                }
            },
            onPlay = onPlay,
        )
    }
}

@Composable
private fun EpisodePickerContent(
    api: AnixartApi,
    settingsStore: SettingsStore,
    release: Release,
    dubbers: List<Dubber>,
    sources: List<Source>,
    episodes: List<Episode>,
    selectedDubber: Int?,
    selectedSource: Int?,
    loadingEpisodes: Boolean,
    onDubberSelect: (Dubber) -> Unit,
    onSourceSelect: (Source) -> Unit,
    onPlay: (Int, Int, Int, Int, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Выбор эпизода",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (episodes.isNotEmpty() && selectedDubber != null && selectedSource != null) {
                val sourceName = sources.firstOrNull { it.id == selectedSource }?.name ?: "Kodik"
                if (isDownloadableSource(sourceName)) {
                    DownloadAllButton(
                        api = api,
                        settingsStore = settingsStore,
                        release = release,
                        dubberId = selectedDubber,
                        sourceId = selectedSource,
                        sourceName = sourceName,
                        episodes = episodes,
                    )
                }
            }
            DeleteAllButton(
                settingsStore = settingsStore,
                release = release,
                episodes = episodes,
                sourceId = selectedSource ?: 0,
            )
        }

        if (dubbers.isNotEmpty()) {
            Text("Озвучка", fontSize = 13.sp, color = ThirdText)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dubbers.forEach { d ->
                    Chip(
                        label = d.name,
                        selected = selectedDubber == d.id,
                        onClick = { onDubberSelect(d) },
                    )
                }
            }
        }

        if (sources.isNotEmpty()) {
            Text("Источник", fontSize = 13.sp, color = ThirdText)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sources.forEach { s ->
                    Chip(
                        label = s.name,
                        selected = selectedSource == s.id,
                        onClick = { onSourceSelect(s) },
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        when {
            loadingEpisodes -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            episodes.isEmpty() -> Text("Нет серий", color = ThirdText, modifier = Modifier.padding(24.dp))
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(episodes) { ep ->
                    val sourceName = sources.firstOrNull { it.id == selectedSource }?.name ?: "Kodik"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SelectButton)
                            .clickable {
                                val dubberId = selectedDubber ?: return@clickable
                                val sourceId = selectedSource ?: return@clickable
                                onPlay(release.id, dubberId, sourceId, ep.position, sourceName)
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            ep.position.toString(),
                            fontSize = 14.sp,
                            color = ThirdText,
                            modifier = Modifier.width(36.dp),
                        )
                        Text(
                            ep.name,
                            fontSize = 15.sp,
                            fontWeight = if (ep.isWatched) FontWeight.Normal else FontWeight.SemiBold,
                            color = if (ep.isWatched) ThirdText else MainText,
                            modifier = Modifier.weight(1f),
                        )
                        if (ep.isWatched) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color(0xFF52B628),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        if (isDownloadableSource(sourceName)) {
                            EpisodeDownloadButton(
                                api = api,
                                settingsStore = settingsStore,
                                release = release,
                                dubberId = selectedDubber,
                                sourceId = selectedSource,
                                sourceName = sourceName,
                                episode = ep,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadAllButton(
    api: AnixartApi,
    settingsStore: SettingsStore,
    release: Release,
    dubberId: Int,
    sourceId: Int,
    sourceName: String,
    episodes: List<Episode>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var states by remember { mutableStateOf<Map<String, EpisodeDownloader.State>>(emptyMap()) }
    LaunchedEffect(Unit) {
        EpisodeDownloader.states.collect { states = it }
    }
    val keys = episodes.map { EpisodeDownloader.key(release.id, sourceId, it.position) }.toSet()
    val downloading = states.filterKeys { it in keys }.values.count { it is EpisodeDownloader.State.Downloading }
    var showQualityDialog by remember { mutableStateOf(false) }

    fun downloadAll(quality: Int) {
        scope.launch {
            for (ep in episodes) {
                val key = EpisodeDownloader.key(release.id, sourceId, ep.position)
                if (EpisodeDownloader.isDownloading(key)) continue
                if (settingsStore.isEpisodeDownloaded(key)) continue
                EpisodeDownloader.download(
                    context = context,
                    settingsStore = settingsStore,
                    api = api,
                    release = release,
                    dubberId = dubberId,
                    sourceId = sourceId,
                    sourceName = sourceName,
                    episode = ep,
                    quality = quality,
                )
            }
        }
    }

    fun stopAll() {
        keys.forEach { EpisodeDownloader.stop(it) }
    }

    if (downloading > 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = ::stopAll)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = PlayerRed,
            )
            Text(
                " $downloading/${episodes.size}",
                fontSize = 13.sp,
                color = ThirdText,
            )
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Остановить все загрузки",
                tint = Carmine,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(14.dp),
            )
        }
    } else {
        TextButton(onClick = { showQualityDialog = true }, shape = RoundedCornerShape(8.dp)) {
            Icon(
                imageVector = Icons.Filled.FileDownload,
                contentDescription = null,
                tint = Carmine,
                modifier = Modifier.size(16.dp),
            )
            Text(" Скачать всё", color = Carmine, fontSize = 13.sp)
        }
    }

    val firstEpisode = episodes.firstOrNull()
    if (showQualityDialog && firstEpisode != null) {
        DownloadQualityDialog(
            episode = firstEpisode,
            sourceName = sourceName,
            onDismiss = { showQualityDialog = false },
            onPick = { quality ->
                showQualityDialog = false
                downloadAll(quality)
            },
        )
    }
}

@Composable
private fun DeleteAllButton(
    settingsStore: SettingsStore,
    release: Release,
    episodes: List<Episode>,
    sourceId: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmOpen by remember { mutableStateOf(false) }
    val downloaded by settingsStore.downloadedEpisodes.collectAsState(initial = emptySet())
    val hasDownloaded = downloaded.any { it.startsWith("${release.id}:") }
    if (!hasDownloaded) return

    TextButton(onClick = { confirmOpen = true }, shape = RoundedCornerShape(8.dp)) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = null,
            tint = ThirdText,
            modifier = Modifier.size(16.dp),
        )
        Text(" Удалить всё", color = ThirdText, fontSize = 13.sp)
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Удалить всё") },
            text = { Text("Удалить папку со скачанными сериями «${release.titleRu}»?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmOpen = false
                        scope.launch {
                            EpisodeDownloader.deleteAll(
                                context = context,
                                settingsStore = settingsStore,
                                release = release,
                                keys = episodes.map {
                                    EpisodeDownloader.key(release.id, sourceId, it.position)
                                },
                            )
                        }
                    },
                ) {
                    Text("Удалить", color = Carmine)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun EpisodeDownloadButton(
    api: AnixartApi,
    settingsStore: SettingsStore,
    release: Release,
    dubberId: Int?,
    sourceId: Int?,
    sourceName: String,
    episode: Episode,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = remember(release.id, sourceId, episode.position) {
        EpisodeDownloader.key(release.id, sourceId ?: 0, episode.position)
    }
    var flowState by remember { mutableStateOf<EpisodeDownloader.State>(EpisodeDownloader.stateOf(key)) }
    var persistedDone by remember { mutableStateOf(false) }

    LaunchedEffect(key) {
        EpisodeDownloader.states.collect { map ->
            flowState = map[key] ?: EpisodeDownloader.State.Idle
        }
    }
    LaunchedEffect(key) {
        persistedDone = settingsStore.isEpisodeDownloaded(key)
    }

    var showQualityDialog by remember { mutableStateOf(false) }

    fun startDownload(quality: Int) {
        val d = dubberId ?: return
        val s = sourceId ?: return
        EpisodeDownloader.download(
            context = context,
            settingsStore = settingsStore,
            api = api,
            release = release,
            dubberId = d,
            sourceId = s,
            sourceName = sourceName,
            episode = episode,
            quality = quality,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showQualityDialog = true
        } else {
            Toast.makeText(context, "Нет разрешения на запись", Toast.LENGTH_SHORT).show()
        }
    }

    fun onDownloadClick() {
        if (Build.VERSION.SDK_INT < 29 &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        showQualityDialog = true
    }

    val effective = when (val s = flowState) {
        is EpisodeDownloader.State.Downloading -> s
        is EpisodeDownloader.State.Failed -> s
        is EpisodeDownloader.State.Done -> s
        EpisodeDownloader.State.Idle -> {
            if (persistedDone) EpisodeDownloader.State.Done else EpisodeDownloader.State.Idle
        }
    }

    Box(modifier = Modifier.padding(start = 8.dp)) {
        when (effective) {
            is EpisodeDownloader.State.Idle -> Icon(
                imageVector = Icons.Filled.FileDownload,
                contentDescription = "Скачать",
                tint = SecondaryText,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = ::onDownloadClick)
                    .padding(2.dp),
            )
            is EpisodeDownloader.State.Downloading -> Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable { EpisodeDownloader.stop(key) },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { effective.progress },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = PlayerRed,
                    trackColor = SelectButton,
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Остановить загрузку",
                    tint = MainText,
                    modifier = Modifier.size(11.dp),
                )
            }
            is EpisodeDownloader.State.Done -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Скачано (нажмите, чтобы удалить)",
                tint = Color(0xFF52B628),
                modifier = Modifier
                    .size(20.dp)
                    .clickable {
                        scope.launch {
                            EpisodeDownloader.deleteDownloaded(context, settingsStore, key)
                        }
                    }
                    .padding(2.dp),
            )
            is EpisodeDownloader.State.Failed -> Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Ошибка, повторить",
                tint = Carmine,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = ::onDownloadClick)
                    .padding(2.dp),
            )
        }
    }

    if (showQualityDialog) {
        DownloadQualityDialog(
            episode = episode,
            sourceName = sourceName,
            onDismiss = { showQualityDialog = false },
            onPick = { quality ->
                showQualityDialog = false
                startDownload(quality)
            },
        )
    }
}

@Composable
private fun DownloadQualityDialog(
    episode: Episode,
    sourceName: String,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    var options by remember(episode.url, sourceName) {
        mutableStateOf<List<Pair<Int, String>>?>(null)
    }
    LaunchedEffect(episode.url, sourceName) {
        val links = runCatching { SourceParsers.parse(episode.url, sourceName) }
            .getOrDefault(emptyMap())
        options = PlayerQuality.labels(links)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Качество для скачивания") },
        text = {
            val opts = options
            if (opts == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
            } else {
                Column {
                    opts.forEach { (quality, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPick(quality) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = false, onClick = { onPick(quality) })
                            Text(label, color = MainText, fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) { Text("Отмена") }
        },
    )
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) PlayerRed else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) Color.White else MainText,
        modifier = modifier,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
