package ru.anidesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.UserCollection
import ru.anidesk.app.ui.theme.AltBackground
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.ThirdText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToCollectionSheet(
    api: AnixartApi,
    profileId: Int,
    releaseId: Int,
    onDismiss: () -> Unit,
    onCreateCollection: () -> Unit,
    onAdded: (UserCollection) -> Unit,
) {
    var collections by remember { mutableStateOf<List<UserCollection>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var addingId by remember { mutableIntStateOf(0) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(profileId, reloadKey) {
        loading = true
        loadError = null
        try {
            collections = api.profileCollections(profileId, 0).content
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = e.networkAwareMessage("Не удалось загрузить подборки:")
        } finally {
            loading = false
        }
    }

    fun addTo(collection: UserCollection) {
        if (addingId != 0) return
        addingId = collection.id
        actionError = null
        scope.launch {
            val result = runCatching { api.collectionReleaseAdd(collection.id, releaseId) }
            addingId = 0
            val response = result.getOrNull()
            if (response == null) {
                actionError = "Не удалось добавить релиз: ${result.exceptionOrNull()?.message ?: "неизвестная ошибка"}"
                return@launch
            }
            when (response.code) {
                0 -> onAdded(collection)
                2 -> actionError = "Подборка не найдена"
                3 -> actionError = "Это не ваша подборка"
                4 -> actionError = "Некорректный релиз"
                5 -> actionError = "Этот релиз уже находится в вашей коллекции"
                6 -> actionError = "Подборка удалена"
                7 -> actionError = "Вы превысили допустимый лимит релизов в коллекции"
                else -> actionError = "Не удалось добавить релиз (код ${response.code})"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.statusBarsPadding(),
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Выберите подборку",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MainText,
            )
            Spacer(Modifier.height(12.dp))
            when {
                loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        color = Carmine,
                    )
                }

                loadError != null -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(loadError!!, color = Carmine, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { reloadKey += 1 }) {
                        Text("Повторить")
                    }
                }

                collections.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "У вас пока нет подборок",
                        color = SecondaryText,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Создайте первую подборку, и релиз сразу попадёт в неё.",
                        color = ThirdText,
                        fontSize = 12.sp,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(collections, key = { it.id }) { collection ->
                        CollectionPickerRow(
                            collection = collection,
                            enabled = addingId == 0,
                            busy = addingId == collection.id,
                            onClick = { addTo(collection) },
                        )
                    }
                }
            }
            if (actionError != null) {
                Spacer(Modifier.height(8.dp))
                Text(actionError!!, color = Carmine, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onCreateCollection,
                enabled = addingId == 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Carmine),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Создать подборку", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CollectionPickerRow(
    collection: UserCollection,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val coverUrl = collection.displayCoverUrl()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AltBackground)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 88.dp, height = 50.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Collections,
                    contentDescription = null,
                    tint = ThirdText,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = collection.title.ifBlank { "Подборка" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MainText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    val count = maxOf(collection.releaseCount, collection.releases.size)
                    append("$count ${releaseWord(count)}")
                    if (collection.isPrivate) append(" • только для меня")
                },
                fontSize = 12.sp,
                color = ThirdText,
            )
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = Carmine,
            )
        }
    }
}

private fun releaseWord(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "релиз"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "релиза"
    else -> "релизов"
}
