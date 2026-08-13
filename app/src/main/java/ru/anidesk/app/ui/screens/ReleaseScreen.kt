package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Dubber
import ru.anidesk.app.core.network.Episode
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.core.network.Source
import ru.anidesk.app.core.settings.SettingsStore
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.Format
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.PlayerRed
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.SelectButton
import ru.anidesk.app.ui.theme.ThirdText

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
    var showEpisodePicker by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    var bookmarkType by remember { mutableIntStateOf(0) }
    var showBookmarkMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                item { ReleaseHeader(r, onBack = onBack) }
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
                            modifier = Modifier.weight(1f).height(46.dp),
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
                        if (!r.note.isNullOrEmpty()) {
                            Text(
                                text = r.note,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(10.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                        }
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
                if (r.screenshotImages.isNotEmpty()) {
                    item {
                        Text(
                            "Кадры",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(r.screenshotImages) { img ->
                                AsyncImage(
                                    model = img,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .width(220.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
                if (r.relatedReleases.isNotEmpty()) {
                    item {
                        Text(
                            "Связанные релизы",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(r.relatedReleases) { related ->
                                Column(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .clickable { onOpenRelease(related.id) },
                                ) {
                                    AsyncImage(
                                        model = related.image,
                                        contentDescription = related.nameRu,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Text(
                                        related.nameRu,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        AsyncImage(
            model = r.image,
            contentDescription = r.titleRu,
            modifier = Modifier
                .width(130.dp)
                .height(185.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                r.titleRu,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            if (r.titleOriginal.isNotEmpty()) {
                Text(r.titleOriginal, fontSize = 14.sp, color = SecondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Выбор эпизода",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

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
                            onClick = {
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
                            onClick = {
                                selectedSource = s.id
                                scope.launch {
                                    loadEpisodes()
                                    selectedDubber?.let {
                                        settingsStore.setDubberSource(release.id, it, s.id)
                                    }
                                }
                            },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            when {
                loadingEpisodes -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                episodes.isEmpty() -> Text("Нет серий", color = ThirdText, modifier = Modifier.padding(24.dp))
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
                            if (ep.isWatched) Text("✓", color = Color(0xFF52B628))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) PlayerRed else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) Color.White else MainText,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
