package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.settings.SettingsStore
import ru.anidesk.app.ui.components.isTv
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.ThirdText

private val THEME_OPTIONS = listOf(
    0 to "Системная",
    1 to "Светлая",
    2 to "Тёмная",
)

private val QUALITY_OPTIONS = listOf(
    0 to "Авто",
    1080 to "1080p",
    720 to "720p",
    480 to "480p",
    360 to "360p",
)

private val VIEW_TYPE_OPTIONS = listOf(
    0 to "Сетка",
    1 to "Список",
)

private val REWIND_OPTIONS = listOf(
    5 to "5 секунд",
    10 to "10 секунд",
    15 to "15 секунд",
    20 to "20 секунд",
    30 to "30 секунд",
)

@Composable
fun SettingsScreen(api: AnixartApi, settingsStore: SettingsStore, onBack: () -> Unit) {
    var screen by remember { mutableIntStateOf(0) }

    when (screen) {
        0 -> SettingsMain(settingsStore, onBack = onBack, onOpen = { screen = it })
        1 -> PlaybackSettings(settingsStore, onBack = { screen = 0 })
        2 -> AppearanceSettings(settingsStore, onBack = { screen = 0 })
        3 -> DataSettings(api, settingsStore, onBack = { screen = 0 })
        else -> AboutSettings(onBack = { screen = 0 })
    }
}

// ---------- Главный экран ----------

@Composable
private fun SettingsMain(settingsStore: SettingsStore, onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var theme by remember { mutableIntStateOf(0) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val version = remember {
        ru.anidesk.app.BuildConfig.VERSION_NAME
    }

    LaunchedEffect(Unit) {
        theme = settingsStore.theme.first()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        ScreenTitle("Настройки", onBack = onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SettingsRow(
                icon = Icons.Filled.DarkMode,
                title = "Тёмная тема",
                summary = THEME_OPTIONS.firstOrNull { it.first == theme }?.second ?: "",
                onClick = { showThemeDialog = true },
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.PlayArrow,
                title = "Воспроизведение",
                onClick = { onOpen(1) },
            )
            if (!isTv()) {
                SettingsRow(
                    icon = Icons.Filled.Palette,
                    title = "Оформление",
                    onClick = { onOpen(2) },
                )
            }
            SettingsRow(
                icon = Icons.Filled.Storage,
                title = "Данные",
                onClick = { onOpen(3) },
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Info,
                title = "О приложении",
                summary = "Версия $version",
                onClick = { onOpen(4) },
            )
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Тёмная тема") },
            text = {
                Column {
                    THEME_OPTIONS.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    theme = value
                                    scope.launch { settingsStore.setTheme(value) }
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                color = MainText,
                                modifier = Modifier.weight(1f),
                            )
                            if (theme == value) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Carmine, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Отмена") }
            },
        )
    }
}

// ---------- Воспроизведение ----------

@Composable
private fun PlaybackSettings(settingsStore: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var quality by remember { mutableIntStateOf(0) }
    var rewindTime by remember { mutableIntStateOf(10) }
    var orientationLock by remember { mutableStateOf(false) }
    var autoPlay by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showRewindDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        quality = settingsStore.defaultQuality.first()
        rewindTime = settingsStore.rewindTime.first()
        orientationLock = settingsStore.orientationLock.first()
        autoPlay = settingsStore.autoPlay.first()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        SubScreenHeader("Воспроизведение", onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SettingsRow(
                icon = Icons.Filled.PlayCircle,
                title = "Качество видео по умолчанию",
                summary = QUALITY_OPTIONS.firstOrNull { it.first == quality }?.second ?: "",
                onClick = { showQualityDialog = true },
            )
            if (!isTv()) {
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.FastForward,
                    title = "Шаг перемотки",
                    summary = REWIND_OPTIONS.firstOrNull { it.first == rewindTime }?.second ?: "",
                    onClick = { showRewindDialog = true },
                )
            }
            if (!isTv()) {
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Fullscreen,
                    title = "Только горизонтальная ориентация",
                    summary = "В плеере разрешён только ландшафт",
                    trailing = {
                        Switch(
                            checked = orientationLock,
                            onCheckedChange = {
                                orientationLock = it
                                scope.launch { settingsStore.setOrientationLock(it) }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Carmine),
                        )
                    },
                )
            }
            if (!isTv()) {
                SettingsRow(
                    icon = Icons.Filled.PlayArrow,
                    title = "Автовоспроизведение",
                    summary = "Следующая серия запустится автоматически",
                    trailing = {
                        Switch(
                            checked = autoPlay,
                            onCheckedChange = {
                                autoPlay = it
                                scope.launch { settingsStore.setAutoPlay(it) }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Carmine),
                        )
                    },
                )
            }
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Качество видео по умолчанию") },
            text = {
                Column {
                    QUALITY_OPTIONS.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    quality = value
                                    scope.launch { settingsStore.setDefaultQuality(value) }
                                    showQualityDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                color = MainText,
                                modifier = Modifier.weight(1f),
                            )
                            if (quality == value) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Carmine, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text("Отмена") }
            },
        )
    }

    if (showRewindDialog) {
        AlertDialog(
            onDismissRequest = { showRewindDialog = false },
            title = { Text("Шаг перемотки") },
            text = {
                Column {
                    REWIND_OPTIONS.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    rewindTime = value
                                    scope.launch { settingsStore.setRewindTime(value) }
                                    showRewindDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                color = MainText,
                                modifier = Modifier.weight(1f),
                            )
                            if (rewindTime == value) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Carmine, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRewindDialog = false }) { Text("Отмена") }
            },
        )
    }
}

// ---------- Оформление ----------

@Composable
private fun AppearanceSettings(settingsStore: SettingsStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var viewType by remember { mutableIntStateOf(0) }
    var showViewTypeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewType = settingsStore.viewType.first()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        SubScreenHeader("Оформление", onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SettingsRow(
                icon = Icons.Filled.Palette,
                title = "Вид отображения",
                summary = VIEW_TYPE_OPTIONS.firstOrNull { it.first == viewType }?.second ?: "",
                onClick = { showViewTypeDialog = true },
            )
        }
    }

    if (showViewTypeDialog) {
        AlertDialog(
            onDismissRequest = { showViewTypeDialog = false },
            title = { Text("Вид отображения") },
            text = {
                Column {
                    VIEW_TYPE_OPTIONS.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewType = value
                                    scope.launch { settingsStore.setViewType(value) }
                                    showViewTypeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                color = MainText,
                                modifier = Modifier.weight(1f),
                            )
                            if (viewType == value) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Carmine, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showViewTypeDialog = false }) { Text("Отмена") }
            },
        )
    }
}

// ---------- Данные ----------

@Composable
private fun DataSettings(api: AnixartApi, settingsStore: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheCleared by remember { mutableStateOf(false) }
    var apiEndpoint by remember { mutableStateOf(SettingsStore.DEFAULT_API_ENDPOINT) }
    var showApiDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apiEndpoint = settingsStore.apiEndpoint.first()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        SubScreenHeader("Данные", onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SettingsRow(
                icon = Icons.Filled.Storage,
                title = "Эндпоинт API",
                summary = apiEndpoint,
                onClick = { showApiDialog = true },
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.DeleteSweep,
                title = "Очистить кэш изображений",
                summary = if (cacheCleared) "Кэш очищен" else "Удалить загруженные изображения",
                onClick = {
                    scope.launch {
                        val loader = coil3.SingletonImageLoader.get(context)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                        cacheCleared = true
                    }
                },
            )
        }
    }

    if (showApiDialog) {
        AlertDialog(
            onDismissRequest = { showApiDialog = false },
            title = { Text("Эндпоинт API") },
            text = {
                Column {
                    SettingsStore.API_ENDPOINTS.forEach { (host, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    apiEndpoint = host
                                    scope.launch {
                                        settingsStore.setApiEndpoint(host)
                                        api.baseUrl = SettingsStore.apiBaseUrl(host)
                                    }
                                    showApiDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                color = MainText,
                                modifier = Modifier.weight(1f),
                            )
                            if (apiEndpoint == host) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Carmine, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showApiDialog = false }) { Text("Отмена") }
            },
        )
    }
}

// ---------- О приложении ----------

@Composable
private fun AboutSettings(onBack: () -> Unit) {
    val version = remember { ru.anidesk.app.BuildConfig.VERSION_NAME }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        SubScreenHeader("О приложении", onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("AniDesk", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MainText)
            Spacer(Modifier.width(0.dp))
            Text("Версия $version", fontSize = 14.sp, color = ThirdText)
            Text("Разработчик: Smold2", fontSize = 13.sp, color = ThirdText)
        }
    }
}

// ---------- Общие элементы ----------

@Composable
private fun ScreenTitle(title: String, onBack: (() -> Unit)? = null) {
    val tv = isTv()
    val backFocusRequester = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = MainText,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .then(if (tv) Modifier.focusRequester(backFocusRequester) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MainText,
        )
    }
    LaunchedEffect(Unit) {
        if (tv) {
            delay(150)
            backFocusRequester.requestFocus()
        }
    }
}

@Composable
private fun SubScreenHeader(title: String, onBack: () -> Unit) {
    val tv = isTv()
    val backFocusRequester = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Назад",
            tint = MainText,
            modifier = Modifier
                .clickable(onClick = onBack)
                .then(if (tv) Modifier.focusRequester(backFocusRequester) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MainText,
        )
    }
    LaunchedEffect(Unit) {
        if (tv) {
            delay(150)
            backFocusRequester.requestFocus()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tv = isTv()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(
                start = if (tv) 46.dp else 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Carmine,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(text = title, fontSize = 15.sp, color = MainText)
            if (!summary.isNullOrEmpty()) {
                Text(text = summary, fontSize = 13.sp, color = ThirdText)
            }
        }
        trailing?.invoke()
        if (onClick != null && trailing == null) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = SecondaryText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceVariant,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 56.dp),
    )
}
