package ru.anidesk.app.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.util.Rational
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ZoomOutMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Episode
import ru.anidesk.app.player.SourceParsers
import ru.anidesk.app.ui.theme.PlayerRed

private val PLAYER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

private val SPEED_OPTIONS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    api: AnixartApi,
    releaseId: Int,
    dubberId: Int,
    sourceId: Int,
    startPosition: Int,
    sourceName: String,
    settingsStore: ru.anidesk.app.core.settings.SettingsStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var releaseTitle by remember { mutableStateOf("") }
    var currentEpisode by remember { mutableStateOf<Episode?>(null) }
    var links by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var playbackError by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var speedIndex by remember { mutableIntStateOf(3) }
    var resizeMode by remember { mutableIntStateOf(0) }
    var seekTarget by remember { mutableLongStateOf(-1L) }
    var autoPlayNext by remember { mutableStateOf(false) }
    var defaultQuality by remember { mutableIntStateOf(0) }
    var rewindTimeSec by remember { mutableIntStateOf(10) }
    var resumePosition by remember { mutableStateOf<Long?>(null) }

    val dataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent(PLAYER_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
    }

    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            )
            .setTrackSelector(DefaultTrackSelector(context))
            .build()
            .apply { repeatMode = ExoPlayer.REPEAT_MODE_OFF }
    }

    val mediaSession = remember {
        MediaSession.Builder(context.applicationContext, player).build()
    }

    fun positionKey(position: Int) = "$releaseId:$sourceId:$position"

    fun savePosition() {
        val ep = currentEpisode ?: return
        val pos = player.currentPosition
        if (pos >= 5000) {
            scope.launch { settingsStore.setPlaybackPosition(positionKey(ep.position), pos) }
        }
    }

    fun clearPosition(ep: Episode) {
        scope.launch { settingsStore.clearPlaybackPosition(positionKey(ep.position)) }
    }

    LaunchedEffect(Unit) {
        autoPlayNext = settingsStore.autoPlay.first()
        defaultQuality = settingsStore.defaultQuality.first()
        speedIndex = settingsStore.playbackSpeed.first()
        rewindTimeSec = settingsStore.rewindTime.first()
        val lockOrientation = settingsStore.orientationLock.first()
        if (lockOrientation) {
            (context as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val window = (context as? Activity)?.window
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    DisposableEffect(isPortrait, isLightTheme) {
        val insetsController = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.isAppearanceLightStatusBars = false
        insetsController?.isAppearanceLightNavigationBars = false
        if (isPortrait) {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            insetsController?.isAppearanceLightStatusBars = isLightTheme
            insetsController?.isAppearanceLightNavigationBars = isLightTheme
        }
    }

    DisposableEffect(isPlaying) {
        if (isPlaying) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Аудиофокус: пауза/заглушение при звонке и других приложениях (как в AnixartM)
    DisposableEffect(Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var wasPlaying = false
        var focusListener: AudioManager.OnAudioFocusChangeListener? = null
        focusListener = AudioManager.OnAudioFocusChangeListener { focus ->
            when (focus) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    player.pause()
                    audioManager.abandonAudioFocus(focusListener)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    wasPlaying = player.playWhenReady
                    player.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    if (player.playWhenReady) player.volume = 0.2f
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (wasPlaying || player.playWhenReady) {
                        player.play()
                        player.volume = 1f
                    }
                    wasPlaying = false
                }
            }
        }
        audioManager.requestAudioFocus(
            focusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN,
        )
        onDispose {
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    // Пауза при отключении наушников / аудио-шум
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    player.pause()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun buildMediaItem(url: String, ep: Episode): MediaItem =
        MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(releaseTitle)
                    .setArtist(ep.name)
                    .build()
            )
            .build()

    fun pickUrl(links: Map<String, String>, quality: Int): String? = when {
        quality > 0 && links[quality.toString()] != null -> links[quality.toString()]
        links["1080"] != null -> links["1080"]
        links["720"] != null -> links["720"]
        links["480"] != null -> links["480"]
        links["360"] != null -> links["360"]
        else -> links.values.firstOrNull()
    }

    suspend fun playEpisode(ep: Episode) {
        loading = true
        error = null
        try {
            val parsed = SourceParsers.parse(ep.url, sourceName)
            if (parsed.isEmpty()) {
                error = "Не удалось получить ссылку на видео"
                return
            }
            links = parsed
            val url = pickUrl(parsed, defaultQuality)
            if (url == null) {
                error = "Нет доступного качества"
                return
            }

            if (sourceName == "Sibnet") {
                dataSourceFactory.setDefaultRequestProperties(
                    mapOf("Referer" to ep.url, "Host" to "video.sibnet.ru")
                )
            } else {
                dataSourceFactory.setDefaultRequestProperties(emptyMap())
            }

            player.setMediaItem(buildMediaItem(url, ep))
            player.prepare()
            player.play()
            currentEpisode = ep

            if (api.token != null) {
                runCatching { api.markEpisodeAsWatched(releaseId, sourceId, ep.position) }
                runCatching { api.addToHistory(releaseId, sourceId, ep.position) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Ошибка воспроизведения: ${e.message}"
        } finally {
            loading = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        buffering = false
                        val d = player.duration
                        durationMs = if (d > 0) d else 0L
                        positionMs = player.currentPosition
                        controlsVisible = true
                        savePosition()
                    }
                    Player.STATE_BUFFERING -> {
                        buffering = true
                        controlsVisible = false
                    }
                    Player.STATE_ENDED -> {
                        buffering = false
                        val ep = currentEpisode
                        if (ep != null) clearPosition(ep)
                        val index = episodes.indexOfFirst { it.position == currentEpisode?.position }
                        val next = if (autoPlayNext) episodes.getOrNull(index + 1) else null
                        if (next != null) {
                            scope.launch { playEpisode(next) }
                        } else {
                            onBack()
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val d = player.duration
                durationMs = if (d > 0) d else 0L
                positionMs = 0L
            }

            override fun onPlayerError(errorException: androidx.media3.common.PlaybackException) {
                playbackError = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            mediaSession.release()
            player.release()
        }
    }

    LaunchedEffect(isPlaying) {
        var ticks = 0
        while (isPlaying) {
            positionMs = player.currentPosition
            delay(500)
            if (++ticks % 10 == 0) savePosition()
        }
    }

    LaunchedEffect(controlsVisible, locked, isPlaying) {
        if (controlsVisible && !locked && isPlaying) {
            delay(5000)
            controlsVisible = false
        }
    }

    LaunchedEffect(speedIndex) {
        player.playbackParameters = PlaybackParameters(SPEED_OPTIONS[speedIndex])
    }

    LaunchedEffect(Unit) {
        try {
            val res = api.getEpisodes(releaseId, dubberId, sourceId)
            episodes = res.episodes
            releaseTitle = api.releaseInfo(releaseId).release?.titleRu ?: ""
            val start = episodes.firstOrNull { it.position == startPosition } ?: episodes.firstOrNull()
            if (start != null) {
                playEpisode(start)
                val saved = settingsStore.getPlaybackPosition(positionKey(start.position))
                if (saved > 0) resumePosition = saved
            } else {
                error = "Серии не найдены"
                loading = false
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не удалось загрузить серии: ${e.message}"
            loading = false
        }
    }

    val currentIndex = episodes.indexOfFirst { it.position == currentEpisode?.position }

    fun switchEpisode(ep: Episode) {
        savePosition()
        scope.launch { playEpisode(ep) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (!locked) {
                            seekStartPos = player.currentPosition
                            dragAccum = 0f
                            controlsVisible = false
                            player.pause()
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccum += dragAmount
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val seconds = (dragAccum / w * 100f).let { it * it / 25f }
                        val deltaMs = (seconds * 1000L).toLong() * if (dragAccum < 0) -1 else 1
                        seekTarget = if (durationMs > 0) {
                            (seekStartPos + deltaMs).coerceIn(0L, durationMs)
                        } else {
                            (seekStartPos + deltaMs).coerceAtLeast(0L)
                        }
                    },
                    onDragEnd = {
                        if (seekTarget >= 0) {
                            player.seekTo(seekTarget)
                            positionMs = seekTarget
                        }
                        seekTarget = -1
                        player.play()
                        controlsVisible = false
                    },
                    onDragCancel = {
                        seekTarget = -1
                        player.play()
                    },
                )
            }
            .clickable(enabled = !locked) {
                controlsVisible = !controlsVisible
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = {
                it.player = player
                it.resizeMode = when (resizeMode) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (loading || buffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }

        if (seekTarget >= 0) {
            val delta = seekTarget - seekStartPos
            val sign = if (delta >= 0) "+" else "-"
            Text(
                text = sign + formatTime(kotlin.math.abs(delta)),
                color = Color.White,
                fontSize = 32.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        // ---------- Верхняя панель ----------
        if (controlsVisible && !locked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .statusBarsPadding()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White,
                    modifier = Modifier
                        .clickable {
                            savePosition()
                            onBack()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    Text(
                        text = releaseTitle,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    currentEpisode?.let {
                        Text(
                            text = it.name,
                            color = Color(0xFFE0E0E0),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                SelectorChip(
                    icon = { Icon(Icons.Filled.Speed, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    label = formatSpeed(SPEED_OPTIONS[speedIndex]),
                    onClick = {},
                ) {
                    SPEED_OPTIONS.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(formatSpeed(option)) },
                            onClick = {
                                speedIndex = index
                                scope.launch { settingsStore.setPlaybackSpeed(index) }
                            },
                        )
                    }
                }
                SelectorChip(
                    icon = { Icon(Icons.Filled.AspectRatio, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    label = if (defaultQuality == 0) "Авто" else "${defaultQuality}p",
                    onClick = {},
                ) {
                    val qualityItems = buildList {
                        add(0 to "Авто")
                        links.keys
                            .mapNotNull { it.toIntOrNull() }
                            .sortedDescending()
                            .forEach { add(it to "${it}p") }
                    }
                    qualityItems.forEach { (quality, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                defaultQuality = quality
                                scope.launch { settingsStore.setDefaultQuality(quality) }
                                val ep = currentEpisode
                                if (ep != null && links.isNotEmpty()) {
                                    val pos = player.currentPosition
                                    val url = pickUrl(links, quality)
                                    if (url != null) {
                                        player.setMediaItem(buildMediaItem(url, ep))
                                        player.prepare()
                                        player.seekTo(pos)
                                        player.play()
                                        positionMs = pos
                                    }
                                }
                            },
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Заблокировать",
                    tint = Color.White,
                    modifier = Modifier
                        .clickable { locked = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        // ---------- Центр: навигация по сериям ----------
        if (controlsVisible && !locked && links.isNotEmpty() && currentEpisode != null) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                PlayerRoundIcon(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = "Предыдущая серия",
                    size = 32.dp,
                    enabled = currentIndex > 0,
                    onClick = {
                        val prev = episodes.getOrNull(currentIndex - 1) ?: return@PlayerRoundIcon
                        switchEpisode(prev)
                    },
                )
                Spacer(Modifier.width(32.dp))
                PlayerRoundIcon(
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                    size = 56.dp,
                    enabled = true,
                    onClick = {
                        if (player.isPlaying) {
                            savePosition()
                            player.pause()
                        } else {
                            player.play()
                        }
                    },
                )
                Spacer(Modifier.width(32.dp))
                PlayerRoundIcon(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = "Следующая серия",
                    size = 32.dp,
                    enabled = currentIndex in 0 until episodes.size - 1,
                    onClick = {
                        val next = episodes.getOrNull(currentIndex + 1) ?: return@PlayerRoundIcon
                        switchEpisode(next)
                    },
                )
            }
        }

        // ---------- Нижняя панель ----------
        if (controlsVisible && !locked && links.isNotEmpty() && currentEpisode != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(top = 12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(positionMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                    )
                    val sliderRange = if (durationMs > 0) 0f..durationMs.toFloat() else 0f..1f
                    Slider(
                        value = positionMs.toFloat().coerceIn(sliderRange.start, sliderRange.endInclusive),
                        onValueChange = { positionMs = it.toLong() },
                        onValueChangeFinished = { player.seekTo(positionMs) },
                        valueRange = sliderRange,
                        enabled = durationMs > 0,
                        colors = SliderDefaults.colors(
                            thumbColor = PlayerRed,
                            activeTrackColor = PlayerRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                    )
                    Text(
                        text = formatTime(durationMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                    )
                    BottomBarIcon(
                        icon = Icons.Filled.FastForward,
                        contentDescription = "Перемотать вперёд",
                        onClick = {
                            player.seekTo(player.currentPosition + rewindTimeSec * 1000L)
                        },
                    )
                    BottomBarIcon(
                        icon = Icons.Filled.PictureInPicture,
                        contentDescription = "Картинка в картинке",
                        onClick = {
                            (context as? Activity)?.let { activity ->
                                runCatching {
                                    activity.enterPictureInPictureMode(
                                        PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .build()
                                    )
                                }
                            }
                        },
                    )
                    BottomBarIcon(
                        icon = when (resizeMode) {
                            0 -> Icons.Filled.Fullscreen
                            1 -> Icons.Filled.ZoomOutMap
                            else -> Icons.Filled.AspectRatio
                        },
                        contentDescription = "Масштаб",
                        onClick = {
                            if (isPortrait) {
                                (context as? Activity)?.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                resizeMode = 0
                            } else {
                                resizeMode = (resizeMode + 1) % 3
                            }
                            controlsVisible = true
                        },
                    )
                }
            }
        }

        // ---------- Экран блокировки ----------
        if (locked) {
            Icon(
                imageVector = Icons.Filled.LockOpen,
                contentDescription = "Разблокировать",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .clickable { locked = false }
                    .padding(10.dp),
            )
        }

        error?.let {
            Text(
                text = it,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp),
            )
        }
    }

    // ---------- Диалог продолжения воспроизведения (как в AnixartM) ----------
    resumePosition?.let { saved ->
        AlertDialog(
            onDismissRequest = {
                scope.launch { settingsStore.clearPlaybackPosition(positionKey(startPosition)) }
                resumePosition = null
            },
            title = { Text("Воспроизведение") },
            text = { Text("Продолжить с ${formatTime(saved)}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        player.seekTo(saved)
                        positionMs = saved
                        resumePosition = null
                    },
                ) { Text("Продолжить") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch { settingsStore.clearPlaybackPosition(positionKey(startPosition)) }
                        resumePosition = null
                    },
                ) { Text("Начать сначала") }
            },
        )
    }

    // ---------- Диалог ошибки воспроизведения (как в AnixartM) ----------
    if (playbackError) {
        AlertDialog(
            onDismissRequest = { playbackError = false },
            title = { Text("Ошибка") },
            text = { Text("Невозможно воспроизвести видео в выбранном плеере. Попробуйте использовать Веб-плеер.") },
            confirmButton = {
                TextButton(onClick = { playbackError = false }) { Text("ОК") }
            },
        )
    }
}

// Позиция начала жеста перемотки и накопленное смещение (состояние жеста)
private var seekStartPos: Long = 0L
private var dragAccum: Float = 0f

@Composable
private fun SelectorChip(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    menuContent: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Icon(Icons.Filled.ArrowDropDown, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menuContent()
        }
    }
}

@Composable
private fun PlayerRoundIcon(
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.35f),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier
                .size(size)
                .padding(4.dp),
        )
    }
}

@Composable
private fun BottomBarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = Color.White,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
            .size(20.dp),
    )
}

private fun formatSpeed(speed: Float): String {
    return if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
