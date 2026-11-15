package ru.anidesk.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.core.network.UserCollection
import ru.anidesk.app.ui.components.TabHeader
import ru.anidesk.app.ui.theme.AltBackground
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.ThirdText
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@Composable
internal fun CollectionEditorScreen(
    api: AnixartApi,
    existing: UserCollection?,
    initialReleases: List<Release>,
    applyWindowInsets: Boolean = true,
    onCancel: () -> Unit,
    onSaved: (UserCollection, List<Release>) -> Unit,
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var isPrivate by remember(existing?.id) { mutableStateOf(existing?.isPrivate ?: false) }
    var releases by remember(existing?.id) { mutableStateOf(initialReleases) }
    var showAddDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var coverBytes by remember(existing?.id) { mutableStateOf<ByteArray?>(null) }
    var coverPreview by remember(existing?.id) { mutableStateOf<ImageBitmap?>(null) }
    var coverLoading by remember { mutableStateOf(false) }
    var coverError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coverLoading = true
            coverError = null
            scope.launch {
                try {
                    val prepared = prepareCollectionCover(context, uri)
                    coverBytes = prepared.bytes
                    coverPreview = prepared.preview
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    coverError = e.networkAwareMessage("Не удалось подготовить обложку:")
                } finally {
                    coverLoading = false
                }
            }
        }
    }

    BackHandler { if (!saving) onCancel() }

    fun save() {
        if (coverLoading) return
        val trimmedTitle = title.trim()
        val trimmedDescription = description.trim()
        if (trimmedTitle.length !in 10..60) {
            error = "Название должно содержать от 10 до 60 символов"
            return
        }
        if (trimmedDescription.length > 1000) {
            error = "Описание не должно превышать 1000 символов"
            return
        }
        if (releases.isEmpty()) {
            error = "Добавьте хотя бы один релиз"
            return
        }
        if (releases.size > 100) {
            error = "В подборке может быть не более 100 релизов"
            return
        }
        saving = true
        error = null
        scope.launch {
            try {
                val response = if (existing == null) {
                    api.createCollection(
                        title = trimmedTitle,
                        description = trimmedDescription,
                        isPrivate = isPrivate,
                        releases = releases.map { it.id },
                    )
                } else {
                    api.updateCollection(
                        id = existing.id,
                        title = trimmedTitle,
                        description = trimmedDescription,
                        isPrivate = isPrivate,
                        releases = releases.map { it.id },
                    )
                }
                val saved = response.collection
                if (saved == null) {
                    error = collectionSaveError(response.code)
                } else {
                    val uploadedImage = coverBytes?.let { bytes ->
                        try {
                            val upload = api.uploadCollectionImage(saved.id, bytes)
                            if (upload.code == 0 && upload.url.isNotBlank()) {
                                upload.url
                            } else {
                                Toast.makeText(
                                    context,
                                    collectionImageError(upload.code),
                                    Toast.LENGTH_LONG,
                                ).show()
                                null
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                e.networkAwareMessage("Не удалось загрузить обложку:"),
                                Toast.LENGTH_LONG,
                            ).show()
                            null
                        }
                    }
                    onSaved(
                        if (uploadedImage != null) saved.copy(image = uploadedImage) else saved,
                        releases,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.networkAwareMessage("Ошибка:")
            } finally {
                saving = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(
                if (applyWindowInsets) {
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                } else {
                    Modifier
                },
            ),
    ) {
        TabHeader(
            title = if (existing == null) "Создание подборки" else "Редактирование подборки",
            onBack = { if (!saving) onCancel() },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val savedCover = existing?.image?.takeIf { it.isNotBlank() }
                val fallbackCover = releases.firstOrNull()?.coverImageUrl()
                val displayedCover = savedCover ?: fallbackCover
                val hasCustomCover = coverPreview != null || savedCover != null
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AltBackground)
                            .clickable(enabled = !saving && !coverLoading) {
                                coverError = null
                                coverPicker.launch("image/*")
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            coverPreview != null -> Image(
                                bitmap = coverPreview!!,
                                contentDescription = "Обложка подборки",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )

                            displayedCover != null -> AsyncImage(
                                model = displayedCover,
                                contentDescription = "Обложка подборки",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )

                            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = ThirdText,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text("Загрузить обложку", color = ThirdText, fontSize = 13.sp)
                            }
                        }
                        if (coverLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(30.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                            }
                        } else if (displayedCover != null || coverPreview != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = if (hasCustomCover) "Изменить обложку" else "Выбрать обложку",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    Text(
                        text = "Если обложка не выбрана, будет использована обложка первого аниме в подборке.",
                        fontSize = 11.sp,
                        color = ThirdText,
                    )
                    if (coverError != null) {
                        Text(coverError!!, color = Carmine, fontSize = 12.sp)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AltBackground)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Приватная подборка",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MainText,
                        )
                        Text(
                            text = "Будет видна только вам",
                            fontSize = 12.sp,
                            color = ThirdText,
                        )
                    }
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        enabled = !saving,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = Carmine,
                        ),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Тайтлы",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MainText,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = releases.size.toString(),
                        fontSize = 13.sp,
                        color = ThirdText,
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Carmine),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить тайтл", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (releases.isEmpty()) {
                item {
                    Text(
                        text = "Пока пусто. Добавьте тайтлы в подборку.",
                        fontSize = 13.sp,
                        color = ThirdText,
                    )
                }
            }
            items(releases, key = { it.id }) { release ->
                EditorReleaseRow(
                    release = release,
                    enabled = !saving,
                    onRemove = { releases = releases.filterNot { it.id == release.id } },
                )
            }
            if (error != null) {
                item {
                    Text(text = error!!, color = Carmine, fontSize = 13.sp)
                }
            }
            item {
                Button(
                    onClick = { save() },
                    enabled = !saving && !coverLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Carmine),
                ) {
                    Text(
                        text = when {
                            saving -> "Сохранение..."
                            existing == null -> "Создать подборку"
                            else -> "Сохранить"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            item {
                TextButton(
                    onClick = { if (!saving) onCancel() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Отмена", color = ThirdText)
                }
            }
        }
    }

    if (showAddDialog) {
        AddReleaseDialog(
            api = api,
            onDismiss = { showAddDialog = false },
            onAdd = { release ->
                if (releases.none { it.id == release.id }) {
                    releases = releases + release
                }
                showAddDialog = false
            },
        )
    }
}

private fun collectionSaveError(code: Int): String = when (code) {
    2 -> "Название должно содержать от 10 до 60 символов"
    3 -> "Описание не должно превышать 1000 символов"
    4 -> "Добавьте хотя бы один релиз"
    5 -> "Достигнут лимит подборок"
    6 -> "Подборка не найдена"
    7 -> "Нет прав на редактирование подборки"
    8 -> "Подборка удалена"
    9 -> "Достигнут лимит релизов в подборке"
    else -> "Не удалось сохранить подборку (код $code)"
}

private fun collectionImageError(code: Int): String = when (code) {
    2 -> "Подборка не найдена"
    else -> "Не удалось загрузить обложку (код $code)"
}

private data class PreparedCover(
    val bytes: ByteArray,
    val preview: ImageBitmap,
)

private suspend fun prepareCollectionCover(context: Context, uri: Uri): PreparedCover =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error("Не удалось прочитать изображение")
        }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 2400 || bounds.outHeight / sampleSize > 2400) {
            sampleSize *= 2
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
        } ?: error("Не удалось прочитать изображение")

        val orientation = resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        val oriented = if (matrix.isIdentity) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }
        if (oriented !== decoded) decoded.recycle()

        val targetRatio = 16f / 9f
        val sourceRatio = oriented.width.toFloat() / oriented.height.toFloat()
        val cropWidth = if (sourceRatio > targetRatio) {
            (oriented.height * targetRatio).roundToInt()
        } else {
            oriented.width
        }
        val cropHeight = if (sourceRatio > targetRatio) {
            oriented.height
        } else {
            (oriented.width / targetRatio).roundToInt()
        }
        val cropped = if (cropWidth == oriented.width && cropHeight == oriented.height) {
            oriented
        } else {
            Bitmap.createBitmap(
                oriented,
                (oriented.width - cropWidth) / 2,
                (oriented.height - cropHeight) / 2,
                cropWidth,
                cropHeight,
            )
        }
        if (cropped !== oriented) oriented.recycle()

        val scaled = Bitmap.createScaledBitmap(cropped, 1200, 675, true)
        if (scaled !== cropped) cropped.recycle()
        val bytes = ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
            output.toByteArray()
        }
        scaled.recycle()
        if (bytes.isEmpty()) error("Не удалось обработать изображение")

        val preview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            .asImageBitmap()
        PreparedCover(bytes, preview)
    }

@Composable
private fun EditorReleaseRow(
    release: Release,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AltBackground)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = if (release.poster.isNotEmpty()) release.poster else release.image.ifEmpty { null },
            contentDescription = null,
            modifier = Modifier
                .size(width = 84.dp, height = 50.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(release.titleRu.ifEmpty { release.titleOriginal.ifEmpty { "Тайтл" } })
                    if (release.year.isNotEmpty()) append(" (${release.year})")
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MainText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Убрать из подборки",
                tint = SecondaryText,
            )
        }
    }
}
