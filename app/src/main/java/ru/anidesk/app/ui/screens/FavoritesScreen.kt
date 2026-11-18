package ru.anidesk.app.ui.screens

import kotlinx.coroutines.delay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.core.settings.SettingsStore
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.components.ReleaseGrid
import ru.anidesk.app.ui.components.TabHeader
import ru.anidesk.app.ui.theme.ThirdText

@Composable
fun FavoritesScreen(
    api: AnixartApi,
    settingsStore: SettingsStore,
    onOpenRelease: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var releases by remember { mutableStateOf<List<Release>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val viewType by settingsStore.viewType.collectAsStateWithLifecycle(initialValue = 0)
    val listMode = viewType == 1

    LaunchedEffect(Unit) {
        try {
            val res = api.favorites(0)
            releases = res.content
            endReached = res.content.isEmpty()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.networkAwareMessage("Не удалось загрузить:")
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        TabHeader("Избранное", onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
        when {
            loading -> LoadingIndicator()
            error != null -> ErrorBox(error!!)
            releases.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Пока пусто", color = ThirdText, fontSize = 14.sp)
            }
            else -> ReleaseGrid(
                releases = releases,
                onOpenRelease = onOpenRelease,
                listMode = listMode,
                loadingMore = loadingMore,
                onLoadMore = {
                    if (!endReached && !loadingMore && releases.isNotEmpty()) {
                        loadingMore = true
                        val next = page + 1
                        var more: List<Release>? = null
                        repeat(3) { attempt ->
                            if (more != null) return@repeat
                            runCatching { api.favorites(next).content }
                                .onSuccess { more = it }
                                .onFailure { e ->
                                    android.util.Log.w(
                                        "Pagination",
                                        "Favorites page=$next attempt=${attempt + 1} failed: ${e.message}",
                                    )
                                    if (attempt < 2) delay(1200)
                                }
                        }
                        android.util.Log.i(
                            "Pagination",
                            "Favorites page=$next got=${more?.size ?: -1} loaded=${releases.size}",
                        )
                        when {
                            more == null -> Unit // ошибка — конец списка не выставляем
                            more!!.isEmpty() -> endReached = true
                            else -> {
                                releases = (releases + more!!).distinctBy { it.id }
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