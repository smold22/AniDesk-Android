package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ClipEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Dubber
import ru.anidesk.app.core.network.Episode
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.core.network.Source
import ru.anidesk.app.core.settings.SettingsStore
import ru.anidesk.app.downloads.EpisodeDownloader
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.Format
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.components.PosterPlaceholder
import ru.anidesk.app.ui.components.isTv
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.PlayerRed
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.SelectButton
import ru.anidesk.app.ui.theme.ThirdText

private val FocusBlue = Color(0xFF4285F4)

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
    val tv = isTv()
    val watchFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(release) {
        if (tv && release != null) {
            delay(150)
            repeat(3) {
                runCatching { watchFocusRequester.requestFocus() }
                delay(180)
                runCatching { listState.scrollToItem(0) }
                delay(60)
            }
            delay(300)
            runCatching { listState.scrollToItem(0) }
            runCatching { watchFocusRequester.requestFocus() }
        }
    }

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
                if (tv) {
                    val backdrop = r.screenshotImages.firstOrNull() ?: r.image
                    if (backdrop.isNotEmpty()) {
                        AsyncImage(
                            model = backdrop,
                            contentDescription = null,
                            modifier = Modifier
                                .matchParentSize()
                                .scale(1.1f)
                                .blur(18.dp),
                            contentScale = ContentScale.Crop,
                        )
                        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.72f)))
                    } else {
                        Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background))
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (tv) Modifier else Modifier.background(MaterialTheme.colorScheme.background))
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                item {
                    if (tv) {
                        TvReleaseHeader(
                            r = r,
                            onBack = onBack,
                            isFavorite = isFavorite,
                            onFavoriteClick = {
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
                            bookmarkType = bookmarkType,
                            onBookmarkSelect = { type ->
                                if (type == bookmarkType) return@TvReleaseHeader
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
                            onPlayClick = { showEpisodePicker = true },
                            playEnabled = (r.episodesReleased ?: 0) > 0 && !r.isViewBlocked,
                            watchFocusRequester = watchFocusRequester,
                        )
                    } else {
                        ReleaseHeader(r, onBack = onBack)
                    }
                }
                if (!tv) {
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
private fun TvReleaseHeader(
    r: Release,
    onBack: () -> Unit,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    bookmarkType: Int,
    onBookmarkSelect: (Int) -> Unit,
    onPlayClick: () -> Unit,
    playEnabled: Boolean,
    watchFocusRequester: FocusRequester,
) {
    var showBookmarkMenu by remember { mutableStateOf(false) }
    val playInteraction = remember { MutableInteractionSource() }
    val isPlayFocused by playInteraction.collectIsFocusedAsState()
    val posterHeight = (LocalConfiguration.current.screenHeightDp - 200).coerceIn(240, 370).dp
    val posterWidth = posterHeight * 0.7f

    Column(Modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Назад",
            tint = MainText,
            modifier = Modifier
                .padding(start = 16.dp, top = 4.dp)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterHeight)
                .padding(start = 32.dp, end = 32.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Spacer(Modifier.height(2.dp))
                Text(
                    r.titleRu,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MainText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (r.titleOriginal.isNotEmpty()) {
                    Text(
                        r.titleOriginal,
                        fontSize = 16.sp,
                        color = SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                val infoLine = buildList {
                    r.year.takeIf { it.isNotEmpty() }?.let { add(it) }
                    r.country.takeIf { it.isNotEmpty() }?.let { add(it) }
                    r.genres.takeIf { it.isNotEmpty() }?.let { add(it) }
                    add("Серий: ${Format.getEpisodeString(r)}")
                    add(String.format("%.2f", r.grade))
                    add("♥ ${r.favoritesCount}")
                }.joinToString(" • ")
                Text(
                    infoLine,
                    fontSize = 16.sp,
                    color = SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (r.description.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        r.description,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MainText,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onPlayClick,
                        enabled = playEnabled,
                        interactionSource = playInteraction,
                        modifier = Modifier
                            .height(44.dp)
                            .focusRequester(watchFocusRequester),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlayFocused) FocusBlue else PlayerRed,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp),
                    ) {
                        Text("Смотреть", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    Box {
                        Button(
                            onClick = { showBookmarkMenu = true },
                            modifier = Modifier.height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SelectButton,
                                contentColor = MainText,
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                        ) {
                            Text("В закладки", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
                                        onBookmarkSelect(type)
                                    },
                                )
                            }
                        }
                    }
                    Button(
                        onClick = onFavoriteClick,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SelectButton,
                            contentColor = MainText,
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (isFavorite) "Убрать из избранного" else "В избранное",
                                tint = if (isFavorite) Carmine else MainText,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                r.favoritesCount.toString(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.width(36.dp))
            Box(
                modifier = Modifier
                    .width(posterWidth)
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                PosterPlaceholder(modifier = Modifier.fillMaxSize())
                AsyncImage(
                    model = r.image,
                    contentDescription = r.titleRu,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun ReleaseHeader(r: Release, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var titleExpanded by remember { mutableStateOf(false) }

    Column {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Назад",
            tint = MainText,
            modifier = Modifier
                .padding(start = 12.dp, top = 4.dp)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
    val chipFocusRequester = remember { FocusRequester() }
    val tv = isTv()
    var firstChipFocused by remember { mutableStateOf(false) }

    LaunchedEffect(dubbers) {
        if (!tv) return@LaunchedEffect
        while (!firstChipFocused) {
            delay(300)
            if (dubbers.isNotEmpty()) {
                runCatching { chipFocusRequester.requestFocus() }
            }
        }
    }

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

    if (tv) {
        BackHandler {
            onDismiss()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0101010))
                .clickable(enabled = false) {},
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
                chipFocusRequester = chipFocusRequester,
                firstChipFocused = firstChipFocused,
                onFirstChipFocused = { firstChipFocused = true },
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
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            sheetGesturesEnabled = false,
            contentWindowInsets = { WindowInsets.safeDrawing },
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
                chipFocusRequester = chipFocusRequester,
                firstChipFocused = firstChipFocused,
                onFirstChipFocused = { firstChipFocused = true },
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
    chipFocusRequester: FocusRequester,
    firstChipFocused: Boolean,
    onFirstChipFocused: () -> Unit,
    onDubberSelect: (Dubber) -> Unit,
    onSourceSelect: (Source) -> Unit,
    onPlay: (Int, Int, Int, Int, String) -> Unit,
) {
    val tv = isTv()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(start = 56.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
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
            if (!tv && episodes.isNotEmpty() && selectedDubber != null && selectedSource != null) {
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
            if (!tv) {
                DeleteAllButton(
                    settingsStore = settingsStore,
                    release = release,
                    episodes = episodes,
                    sourceId = selectedSource ?: 0,
                )
            }
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
                dubbers.forEachIndexed { index, d ->
                    Chip(
                        label = d.name,
                        selected = selectedDubber == d.id,
                        onClick = { onDubberSelect(d) },
                        modifier = if (index == 0) {
                            Modifier
                                .focusRequester(chipFocusRequester)
                                .onFocusChanged {
                                    if (it.isFocused) onFirstChipFocused()
                                }
                        } else {
                            Modifier
                        },
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
            tv -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(episodes.size, key = { episodes[it].position }) { index ->
                    val ep = episodes[index]
                    val sourceName = sources.firstOrNull { it.id == selectedSource }?.name ?: "Kodik"
                    var focused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = {
                            val dubberId = selectedDubber ?: return@Surface
                            val sourceId = selectedSource ?: return@Surface
                            onPlay(release.id, dubberId, sourceId, ep.position, sourceName)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (focused) Carmine else SelectButton,
                        contentColor = if (focused) Color.White else MainText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused },
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    ep.position.toString(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (focused) Color.White else Carmine,
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
                            }
                            Text(
                                ep.name,
                                fontSize = 12.sp,
                                color = if (focused) Color.White.copy(alpha = 0.9f) else ThirdText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(episodes) { ep ->
                    val sourceName = sources.firstOrNull { it.id == selectedSource }?.name ?: "Kodik"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val dubberId = selectedDubber ?: return@clickable
                                val sourceId = selectedSource ?: return@clickable
                                onPlay(release.id, dubberId, sourceId, ep.position, sourceName)
                            }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
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
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = if (tv) 30.dp else 0.dp),
                            )
                        }
                        if (!tv && isDownloadableSource(sourceName)) {
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

    fun downloadAll() {
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
        TextButton(onClick = ::downloadAll) {
            Icon(
                imageVector = Icons.Filled.FileDownload,
                contentDescription = null,
                tint = Carmine,
                modifier = Modifier.size(16.dp),
            )
            Text(" Скачать всё", color = Carmine, fontSize = 13.sp)
        }
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

    TextButton(onClick = { confirmOpen = true }) {
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

    fun startDownload() {
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
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startDownload()
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
        startDownload()
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
        shape = RoundedCornerShape(50),
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
