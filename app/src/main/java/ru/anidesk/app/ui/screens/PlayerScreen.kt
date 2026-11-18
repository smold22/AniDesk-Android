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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
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
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ZoomOutMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Dubber
import ru.anidesk.app.core.network.Episode
import ru.anidesk.app.player.SourceParsers
import ru.anidesk.app.ui.theme.PlayerRed
import java.util.concurrent.atomic.AtomicInteger

private val PLAYER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

private val SPEED_OPTIONS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

/** Шаг перемотки двойным тапом, независимый от настройки «Пропуск». */
private const val DOUBLE_TAP_SEEK_SEC = 15

/** Буфер плеера: держим запас, чтобы сеть не успевала закончиться во время просмотра. */
private const val MIN_BUFFER_MS = 20_000
private const val MAX_BUFFER_MS = 60_000
private const val BUFFER_FOR_PLAYBACK_MS = 2_500
private const val BUFFER_FOR_REBUFFER_MS = 5_000
private const val BACK_BUFFER_MS = 30_000

/** Сколько секунд следующей серии грузим заранее. */
private const val PRELOAD_NEXT_MS = 20_000L

private data class PreloadedEpisode(
    val item: MediaItem,
    val candidates: List<String>,
    val links: Map<String, String>,
)

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
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var speedIndex by remember { mutableIntStateOf(3) }
    var resizeMode by remember { mutableIntStateOf(0) }
    var seekTarget by remember { mutableLongStateOf(-1L) }
    var tapFeedbackId by remember { mutableIntStateOf(0) }
    var seekStartPos by remember { mutableLongStateOf(0L) }
    var dragAccum by remember { mutableStateOf(0f) }
    var tapFeedback by remember { mutableStateOf("") }
    var autoPlayNext by remember { mutableStateOf(false) }
    var defaultQuality by remember { mutableIntStateOf(0) }
    var rewindTimeSec by remember { mutableIntStateOf(10) }
    var resumePosition by remember { mutableStateOf<Long?>(null) }
    var resumeEpisodePosition by remember { mutableIntStateOf(-1) }
    var linkCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var candidateIndex by remember { mutableIntStateOf(0) }
    var dubbers by remember { mutableStateOf<List<Dubber>>(emptyList()) }
    var currentDubberId by remember { mutableIntStateOf(dubberId) }
    var currentSourceId by remember { mutableIntStateOf(sourceId) }
    var currentSourceName by remember { mutableStateOf(sourceName) }

    val dataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent(PLAYER_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
    }

    // Индекс текущей серии читается менеджером предзагрузки из фонового потока.
    val preloadCurrentIndex = remember { AtomicInteger(-1) }

    val preloadBuilder = remember {
        DefaultPreloadManager.Builder(
            context,
            object : TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> {
                override fun getTargetPreloadStatus(rankingData: Int): DefaultPreloadManager.PreloadStatus {
                    // Грузим заранее только следующую серию.
                    return if (rankingData == preloadCurrentIndex.get() + 1) {
                        DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(PRELOAD_NEXT_MS)
                    } else {
                        DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
                    }
                }
            },
        )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        MIN_BUFFER_MS,
                        MAX_BUFFER_MS,
                        BUFFER_FOR_PLAYBACK_MS,
                        BUFFER_FOR_REBUFFER_MS,
                    )
                    .setBackBuffer(BACK_BUFFER_MS, true)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
    }

    val preloadManager = remember { preloadBuilder.build() }

    // Плеер строим тем же билдером: так он использует уже загруженные данные.
    val player = remember {
        preloadBuilder
            .buildExoPlayer(ExoPlayer.Builder(context))
            .apply { repeatMode = ExoPlayer.REPEAT_MODE_OFF }
    }

    var preloadCache by remember { mutableStateOf<Map<Int, PreloadedEpisode>>(emptyMap()) }
    var activePreloadItem by remember { mutableStateOf<MediaItem?>(null) }
    var preloadJob by remember { mutableStateOf<Job?>(null) }

    val mediaSession = remember { mutableStateOf<MediaSession?>(null) }

    fun positionKey(position: Int) = "$releaseId:$currentSourceId:$position"

    fun savePosition() {
        val ep = currentEpisode ?: return
        val pos = player.currentPosition
        if (pos >= 5000) {
            val key = positionKey(ep.position)
            scope.launch { settingsStore.setPlaybackPosition(key, pos) }
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
    DisposableEffect(isPortrait, isLightTheme, controlsVisible, locked) {
        val insetsController = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.isAppearanceLightStatusBars = false
        insetsController?.isAppearanceLightNavigationBars = false
        if (isPortrait || (controlsVisible && !locked)) {
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        fun createMediaSession() {
            mediaSession.value?.release()
            mediaSession.value = MediaSession.Builder(context.applicationContext, player).build()
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            createMediaSession()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (mediaSession.value == null) createMediaSession()
                }
                Lifecycle.Event.ON_STOP -> {
                    player.pause()
                    mediaSession.value?.release()
                    mediaSession.value = null
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mediaSession.value?.release()
            mediaSession.value = null
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
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                        (wasPlaying || player.playWhenReady)
                    ) {
                        player.play()
                    }
                    player.volume = 1f
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

    fun pickUrl(links: Map<String, String>, quality: Int): String? =
        ru.anidesk.app.player.PlayerQuality.pickUrl(links, quality)

    fun orderedCandidates(links: Map<String, String>, quality: Int): List<String> {
        val preferred = pickUrl(links, quality)
        val others = links.entries
            .filter { it.key != "auto" && it.value != preferred }
            .sortedByDescending { it.key.filter(Char::isDigit).toIntOrNull() ?: 0 }
            .map { it.value }
        val master = links["auto"]
        return buildList {
            preferred?.let { add(it) }
            addAll(others)
            master?.let { add(it) }
        }.distinct()
    }

    fun configureDataSourceHeaders() {
        if (currentSourceName.contains("Sibnet", ignoreCase = true)) {
            dataSourceFactory.setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://video.sibnet.ru/",
                    "Origin" to "https://video.sibnet.ru",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                )
            )
        } else {
            dataSourceFactory.setDefaultRequestProperties(emptyMap())
        }
    }

    fun scheduleNextPreload(current: Episode) {
        preloadJob?.cancel()
        val currentIndex = episodes.indexOfFirst { it.position == current.position }
        if (currentIndex < 0) return
        preloadCurrentIndex.set(currentIndex)
        preloadManager.setCurrentPlayingIndex(currentIndex)
        val next = episodes.getOrNull(currentIndex + 1)
        if (next == null || preloadCache.containsKey(next.position)) {
            preloadManager.invalidate()
            return
        }
        preloadJob = scope.launch {
            configureDataSourceHeaders()
            val parsed = runCatching { SourceParsers.parse(next.url, currentSourceName) }
                .onFailure { android.util.Log.w("Player", "preload parse failed: ${it.message}") }
                .getOrNull()
                ?: return@launch
            val candidates = orderedCandidates(parsed, defaultQuality)
            val url = candidates.firstOrNull() ?: return@launch
            val item = buildMediaItem(url, next)
            preloadCache = preloadCache + (next.position to PreloadedEpisode(item, candidates, parsed))
            preloadManager.add(item, currentIndex + 1)
            preloadManager.invalidate()
            val keep = setOf(current.position, next.position)
            preloadCache.keys.filterNot(keep::contains).forEach { pos ->
                val old = preloadCache[pos]
                preloadCache = preloadCache - pos
                if (old?.item != activePreloadItem) old?.item?.let { preloadManager.remove(it) }
            }
            android.util.Log.i("Player", "preloaded next episode pos=${next.position}")
        }
    }

    suspend fun playEpisode(ep: Episode, resumeMs: Long = 0L) {
        loading = true
        error = null
        android.util.Log.i("Player", "playEpisode pos=${ep.position} cached=${preloadCache.containsKey(ep.position)}")
        try {
            configureDataSourceHeaders()
            val cached = preloadCache[ep.position]
            if (cached != null) {
                links = cached.links
                linkCandidates = cached.candidates
                candidateIndex = 0
                if (cached.item != activePreloadItem) {
                    activePreloadItem = cached.item
                    val preloadedSource = preloadManager.getMediaSource(cached.item)
                    if (preloadedSource != null) {
                        player.setMediaSource(preloadedSource)
                    } else {
                        player.setMediaItem(cached.item)
                    }
                } else {
                    // Повторный запуск той же серии: источник уже у плеера.
                    player.setMediaItem(cached.item)
                }
            } else {
                activePreloadItem = null
                val parsed = SourceParsers.parse(ep.url, currentSourceName)
                if (parsed.isEmpty()) {
                    error = "Не удалось получить ссылку на видео"
                    return
                }
                links = parsed
                linkCandidates = orderedCandidates(parsed, defaultQuality)
                candidateIndex = 0
                val url = linkCandidates.firstOrNull()
                if (url == null) {
                    error = "Нет доступного качества"
                    return
                }
                player.setMediaItem(buildMediaItem(url, ep))
            }
            player.prepare()
            if (resumeMs > 0L) {
                player.seekTo(resumeMs)
                positionMs = resumeMs
            }
            player.play()
            currentEpisode = ep
            scheduleNextPreload(ep)

            if (api.token != null) {
                runCatching { api.markEpisodeAsWatched(releaseId, currentSourceId, ep.position) }
                runCatching { api.addToHistory(releaseId, currentSourceId, ep.position) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("Player", "playEpisode failed pos=${ep.position}", e)
            error = e.networkAwareMessage("Ошибка воспроизведения:")
        } finally {
            loading = false
        }
    }

    suspend fun switchDubber(dubber: Dubber) {
        if (dubber.id == currentDubberId) return
        val pos = currentEpisode?.position ?: startPosition
        val posMs = player.currentPosition
        savePosition()
        // Ссылки другого источника нельзя смешивать с уже загруженными.
        val oldPreloadItems = (preloadCache.values.map { it.item } + listOfNotNull(activePreloadItem)).distinct()
        preloadJob?.cancel()
        preloadJob = null
        preloadCache = emptyMap()
        activePreloadItem = null
        preloadCurrentIndex.set(-1)
        preloadManager.setCurrentPlayingIndex(-1)
        preloadManager.invalidate()
        loading = true
        error = null
        try {
            val sources = api.getDubberSources(releaseId, dubber.id).sources
            val source = sources.firstOrNull()
            if (source == null) {
                error = "Нет доступных источников"
                return
            }
            val res = api.getEpisodes(releaseId, dubber.id, source.id).episodes
            currentDubberId = dubber.id
            currentSourceId = source.id
            currentSourceName = source.name
            episodes = res
            val target = res.firstOrNull { it.position == pos } ?: res.firstOrNull()
            if (target == null) {
                error = "Серии не найдены"
                return
            }
            playEpisode(target, resumeMs = if (posMs > 5000L) posMs else 0L)
            oldPreloadItems.forEach { preloadManager.remove(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.networkAwareMessage("Не удалось сменить озвучку:")
        } finally {
            loading = false
        }
    }

    DisposableEffect(player, preloadManager) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (playing &&
                    !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                ) {
                    player.pause()
                }
                isPlaying = player.isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        buffering = false
                        val d = player.duration
                        durationMs = if (d > 0) d else 0L
                        positionMs = player.currentPosition
                        bufferedPositionMs = player.bufferedPosition
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
                        android.util.Log.i(
                            "Player",
                            "ended index=$index next=${next?.position} autoPlayNext=$autoPlayNext",
                        )
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
                bufferedPositionMs = 0L
            }

            override fun onPlayerError(errorException: androidx.media3.common.PlaybackException) {
                android.util.Log.w(
                    "Player",
                    "player error ${errorException.errorCodeName}: ${errorException.message}",
                    errorException,
                )
                val next = candidateIndex + 1
                if (next < linkCandidates.size) {
                    candidateIndex = next
                    val ep = currentEpisode
                    if (ep != null) {
                        val url = linkCandidates[next]
                        player.setMediaItem(buildMediaItem(url, ep))
                        player.prepare()
                        player.play()
                        return
                    }
                }
                playbackError = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            preloadJob?.cancel()
            preloadJob = null
            mediaSession.value?.release()
            mediaSession.value = null
            player.release()
            preloadManager.release()
        }
    }

    LaunchedEffect(isPlaying) {
        var ticks = 0
        while (isPlaying) {
            positionMs = player.currentPosition
            bufferedPositionMs = player.bufferedPosition
            delay(500)
            if (++ticks % 10 == 0) savePosition()
        }
    }

    LaunchedEffect(controlsVisible, locked, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }

    LaunchedEffect(tapFeedbackId) {
        if (tapFeedbackId > 0) {
            delay(700)
            tapFeedback = ""
        }
    }

    LaunchedEffect(speedIndex) {
        player.playbackParameters = PlaybackParameters(SPEED_OPTIONS[speedIndex])
    }

    LaunchedEffect(Unit) {
        try {
            val res = api.getEpisodes(releaseId, currentDubberId, currentSourceId)
            episodes = res.episodes
            dubbers = runCatching { api.getDubbers(releaseId).types }.getOrDefault(emptyList())
            releaseTitle = api.releaseInfo(releaseId).release?.titleRu ?: ""
            val start = episodes.firstOrNull { it.position == startPosition } ?: episodes.firstOrNull()
            if (start != null) {
                // Сначала спрашиваем про продолжение, иначе видео успевает начать играть с нуля.
                val saved = settingsStore.getPlaybackPosition(positionKey(start.position))
                if (saved > 0L) {
                    resumeEpisodePosition = start.position
                    resumePosition = saved
                    loading = false
                } else {
                    playEpisode(start)
                }
            } else {
                error = "Серии не найдены"
                loading = false
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.networkAwareMessage("Не удалось загрузить серии:")
            loading = false
        }
    }

    val currentIndex = episodes.indexOfFirst { it.position == currentEpisode?.position }

    fun switchEpisode(ep: Episode) {
        savePosition()
        scope.launch { playEpisode(ep) }
    }

    fun showTapFeedback(text: String) {
        tapFeedback = text
        tapFeedbackId++
    }

    fun handleDoubleTap(x: Float, width: Float) {
        val step = DOUBLE_TAP_SEEK_SEC * 1000L
        when {
            x < width / 3f -> {
                val target = (player.currentPosition - step).coerceAtLeast(0L)
                player.seekTo(target)
                positionMs = target
                showTapFeedback("−${formatSeekStep(DOUBLE_TAP_SEEK_SEC)}")
            }
            x > width * 2f / 3f -> {
                val target = if (durationMs > 0) {
                    (player.currentPosition + step).coerceAtMost(durationMs)
                } else {
                    player.currentPosition + step
                }
                player.seekTo(target)
                positionMs = target
                showTapFeedback("+${formatSeekStep(DOUBLE_TAP_SEEK_SEC)}")
            }
            else -> {
                if (player.isPlaying) {
                    player.pause()
                    showTapFeedback("Пауза")
                } else {
                    player.play()
                    showTapFeedback("Воспроизведение")
                }
            }
        }
        controlsVisible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(locked) {
                if (locked) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        seekStartPos = player.currentPosition
                        dragAccum = 0f
                        controlsVisible = false
                        player.pause()
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
            .pointerInput(locked, durationMs) {
                if (locked) return@pointerInput
                var lastTapTime = 0L
                var lastTapX = 0f
                var controlsBeforeTap: Boolean? = null
                val tapWindow = viewConfiguration.doubleTapTimeoutMillis
                val tapSlop = 100.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    val isDoubleTap = lastTapTime != 0L &&
                        down.uptimeMillis - lastTapTime < tapWindow &&
                        kotlin.math.abs(up.position.x - lastTapX) < tapSlop
                    if (isDoubleTap) {
                        lastTapTime = 0L
                        controlsBeforeTap?.let { controlsVisible = it }
                        controlsBeforeTap = null
                        handleDoubleTap(up.position.x, size.width.toFloat())
                    } else {
                        lastTapTime = down.uptimeMillis
                        lastTapX = up.position.x
                        controlsBeforeTap = controlsVisible
                        controlsVisible = !controlsVisible
                    }
                }
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

        if (tapFeedback.isNotEmpty()) {
            Text(
                text = tapFeedback,
                color = Color.White,
                fontSize = 28.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        // ---------- Верхняя панель ----------
        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = if (locked) 0.35f else 0.6f))
                    .systemBarsPadding()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!locked) {
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
                                            // Кэш предзагрузки собран под старое качество.
                                            val oldItems = (preloadCache.values.map { it.item } +
                                                listOfNotNull(activePreloadItem)).distinct()
                                            preloadJob?.cancel()
                                            preloadJob = null
                                            preloadCache = emptyMap()
                                            activePreloadItem = null
                                            preloadCurrentIndex.set(-1)
                                            preloadManager.setCurrentPlayingIndex(-1)
                                            preloadManager.invalidate()
                                            player.setMediaItem(buildMediaItem(url, ep))
                                            player.prepare()
                                            player.seekTo(pos)
                                            player.play()
                                            positionMs = pos
                                            oldItems.forEach { preloadManager.remove(it) }
                                            scheduleNextPreload(ep)
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (dubbers.size > 1) {
                        SelectorChip(
                            icon = { Icon(Icons.Filled.RecordVoiceOver, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            label = dubbers.firstOrNull { it.id == currentDubberId }?.name ?: "Озвучка",
                            onClick = {},
                            labelMaxWidth = 140.dp,
                        ) {
                            dubbers.forEach { dubber ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            dubber.name,
                                            color = if (dubber.id == currentDubberId) PlayerRed else Color.Unspecified,
                                        )
                                    },
                                    onClick = { scope.launch { switchDubber(dubber) } },
                                )
                            }
                        }
                    }
                }
                Icon(
                    imageVector = if (locked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                    contentDescription = if (locked) "Разблокировать" else "Заблокировать",
                    tint = Color.White,
                    modifier = Modifier
                        .clickable { locked = !locked }
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
                    .padding(top = 12.dp, bottom = 12.dp),
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
                    BufferedPlayerSlider(
                        value = positionMs.toFloat().coerceIn(sliderRange.start, sliderRange.endInclusive),
                        bufferedValue = bufferedPositionMs.toFloat().coerceIn(sliderRange.start, sliderRange.endInclusive),
                        valueRange = sliderRange,
                        onValueChange = { positionMs = it.toLong() },
                        onValueChangeFinished = { player.seekTo(positionMs) },
                        enabled = durationMs > 0,
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
    fun startEpisodeFromBeginning(clearSaved: Boolean) {
        val epPos = if (resumeEpisodePosition >= 0) resumeEpisodePosition else startPosition
        val ep = episodes.firstOrNull { it.position == epPos } ?: episodes.firstOrNull()
        resumePosition = null
        resumeEpisodePosition = -1
        if (clearSaved) {
            scope.launch { settingsStore.clearPlaybackPosition(positionKey(epPos)) }
        }
        if (ep != null) scope.launch { playEpisode(ep) }
    }

    resumePosition?.let { saved ->
        AlertDialog(
            onDismissRequest = { startEpisodeFromBeginning(clearSaved = true) },
            title = { Text("Воспроизведение") },
            text = { Text("Продолжить с ${formatTime(saved)}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val epPos = if (resumeEpisodePosition >= 0) resumeEpisodePosition else startPosition
                        val ep = episodes.firstOrNull { it.position == epPos } ?: episodes.firstOrNull()
                        resumePosition = null
                        resumeEpisodePosition = -1
                        if (ep != null) scope.launch { playEpisode(ep, resumeMs = saved) }
                    },
                ) { Text("Продолжить") }
            },
            dismissButton = {
                TextButton(onClick = { startEpisodeFromBeginning(clearSaved = true) }) { Text("Начать сначала") }
            },
        )
    }

    // ---------- Диалог ошибки воспроизведения (как в AnixartM) ----------
    if (playbackError) {
        AlertDialog(
            onDismissRequest = { playbackError = false },
            title = { Text("Ошибка") },
            text = { Text("Невозможно воспроизвести видео. Попробовать снова?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        playbackError = false
                        val ep = currentEpisode
                        if (ep != null) {
                            scope.launch { playEpisode(ep) }
                        } else {
                            onBack()
                        }
                    },
                ) { Text("Повторить") }
            },
            dismissButton = {
                TextButton(onClick = { playbackError = false }) { Text("ОК") }
            },
        )
    }
}

// Позиция начала жеста перемотки и накопленное смещение хранятся в состоянии экрана.

@Composable
private fun SelectorChip(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    labelMaxWidth: Dp = Dp.Infinity,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .widthIn(max = labelMaxWidth),
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
        shape = RoundedCornerShape(8.dp),
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

private fun formatSeekStep(sec: Int): String = when {
    sec < 60 -> "$sec с"
    sec % 60 == 0 -> "${sec / 60} мин"
    else -> "${sec / 60} мин ${sec % 60} с"
}

/**
 * Слайдер перемотки с полосой предзагрузки: поверх стандартной дорожки рисуется
 * уже загруженный буфер, чтобы было видно, где видео можно смотреть без ожидания.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BufferedPlayerSlider(
    value: Float,
    bufferedValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val activeProgress = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val bufferedProgress = ((bufferedValue - valueRange.start) / span).coerceIn(activeProgress, 1f)
    val colors = SliderDefaults.colors(
        thumbColor = PlayerRed,
        activeTrackColor = PlayerRed,
        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
        disabledThumbColor = Color.White.copy(alpha = 0.42f),
        disabledActiveTrackColor = PlayerRed.copy(alpha = 0.4f),
        disabledInactiveTrackColor = Color.White.copy(alpha = 0.16f),
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                enabled = enabled,
                colors = colors,
                modifier = Modifier.bufferedTrackOverlay(
                    activeProgress = activeProgress,
                    bufferedProgress = bufferedProgress,
                    color = Color.White.copy(alpha = 0.45f),
                ),
            )
        },
    )
}

/**
 * Дорисовывает за проигранной частью слайдера отрезок буфера.
 * Доли буфера и прогресса передаются в диапазоне `0f..1f`.
 */
private fun Modifier.bufferedTrackOverlay(
    activeProgress: Float,
    bufferedProgress: Float,
    color: Color,
): Modifier = drawWithContent {
    drawContent()

    if (bufferedProgress <= activeProgress) return@drawWithContent

    val trackY = size.height / 2f
    val strokeWidth = 4.dp.toPx()
    val gapPx = 6.dp.toPx()
    val startX = (size.width * activeProgress + gapPx).coerceIn(0f, size.width)
    val endX = (size.width * bufferedProgress).coerceIn(0f, size.width)
    if (startX >= endX) return@drawWithContent

    drawLine(
        color = color,
        start = Offset(startX, trackY),
        end = Offset(endX, trackY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}
