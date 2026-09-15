package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.Format
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.components.TabHeader
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.ThirdText

private data class NotifItem(
    val id: Long,
    val releaseId: Int,
    val title: String,
    val text: String,
    val timestamp: Long,
    val isNew: Boolean,
    val kind: String,
)

@Composable
fun NotificationsScreen(
    api: AnixartApi,
    onOpenRelease: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var items by remember { mutableStateOf<List<NotifItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        error = null
        try {
            val episodes = api.notificationEpisodes(1).content
            val related = api.notificationRelatedReleases(1).content
            val mapped = mutableListOf<NotifItem>()

            episodes.forEach { n ->
                val ep = n.episode
                val release = ep?.release
                val title = release?.titleRu?.ifBlank { release?.title }
                    ?.ifBlank { release?.id?.toString() }
                    ?: "Новый релиз"
                val releaseId = (ep?.releaseId ?: 0L).takeIf { it > 0L }?.toInt()
                    ?: release?.id
                    ?: 0
                mapped += NotifItem(
                    id = n.id,
                    releaseId = releaseId,
                    title = title,
                    text = "Вышла новая серия — смотреть",
                    timestamp = n.timestamp,
                    isNew = n.isNew,
                    kind = "ep",
                )
            }

            related.forEach { n ->
                val release = n.release
                val title = release?.titleRu?.ifBlank { release?.title }
                    ?.ifBlank { release?.id?.toString() }
                    ?: "Похожий тайтл"
                mapped += NotifItem(
                    id = n.id,
                    releaseId = release?.id ?: 0,
                    title = title,
                    text = "Новая серия похожего тайтла",
                    timestamp = n.timestamp,
                    isNew = n.isNew,
                    kind = "rr",
                )
            }

            items = mapped.sortedByDescending { it.timestamp }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не удалось загрузить: ${e.message}"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        TabHeader("Уведомления", onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
        when {
            loading -> LoadingIndicator()
            error != null -> ErrorBox(error!!)
            items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Уведомлений нет", color = ThirdText, fontSize = 14.sp)
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.kind + it.id }) { item ->
                    NotificationRow(item = item, onClick = {
                        if (item.releaseId > 0) onOpenRelease(item.releaseId)
                    })
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: NotifItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(if (item.isNew) Carmine else MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = item.title,
                fontSize = 15.sp,
                fontWeight = if (item.isNew) FontWeight.Bold else FontWeight.Normal,
                color = MainText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.text.isNotEmpty()) {
                Text(
                    text = item.text,
                    fontSize = 13.sp,
                    color = SecondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (item.timestamp > 0) {
                Text(
                    text = Format.getHistoryTime(item.timestamp),
                    fontSize = 12.sp,
                    color = ThirdText,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}