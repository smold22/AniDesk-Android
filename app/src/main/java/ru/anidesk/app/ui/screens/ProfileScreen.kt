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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.core.network.SessionStore
import ru.anidesk.app.ui.components.ErrorBox
import ru.anidesk.app.ui.components.LoadingIndicator
import ru.anidesk.app.ui.components.ReleaseCard
import ru.anidesk.app.ui.components.TabHeader
import ru.anidesk.app.ui.components.isTv
import ru.anidesk.app.ui.theme.AltBackground
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.ThirdText

@Composable
fun ProfileScreen(
    api: AnixartApi,
    sessionStore: SessionStore,
    onOpenRelease: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFavorites: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var profileId by remember { mutableStateOf<Int?>(null) }
    var login by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<Release>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<Release>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val id = sessionStore.profileId.first()
            profileId = id
            if (id != null) {
                val profile = api.profile(id).profile
                login = profile?.login ?: ""
                avatar = profile?.avatar ?: ""
            }
            history = api.history(0).content
            favorites = api.favorites(0).content
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Не удалось загрузить профиль: ${e.message}"
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
        TabHeader("Профиль", onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
        when {
            loading -> LoadingIndicator()
            profileId == null -> GuestLoginState(onOpenLogin)
            error != null -> ErrorBox(error!!)
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = avatar.ifEmpty { null },
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AltBackground),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = login.ifEmpty { "Гость" },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MainText,
                            )
                            Text(
                                text = "ID: ${profileId ?: "-"}",
                                fontSize = 13.sp,
                                color = ThirdText,
                            )
                        }
                        Button(
                            onClick = {
                                scope.launch { sessionStore.clear() }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AltBackground,
                                contentColor = Carmine,
                            ),
                        ) {
                            Text("Выйти", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (history.isNotEmpty()) {
                    item { SectionLabel("Продолжить просмотр") }
                    item {
                        PreviewRow(
                            releases = history,
                            onOpenRelease = onOpenRelease,
                            onOpenAll = onOpenHistory,
                        )
                    }
                }

                if (favorites.isNotEmpty()) {
                    item { SectionLabel("Избранное") }
                    item {
                        PreviewRow(
                            releases = favorites,
                            onOpenRelease = onOpenRelease,
                            onOpenAll = onOpenFavorites,
                        )
                    }
                }

                if (history.isEmpty() && favorites.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Text("Пока пусто", color = ThirdText, fontSize = 14.sp)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun GuestLoginState(onOpenLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Вы не вошли в аккаунт",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MainText,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Войдите, чтобы синхронизировать историю и закладки",
            fontSize = 13.sp,
            color = ThirdText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onOpenLogin,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Carmine),
        ) {
            Text("Войти", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PreviewRow(
    releases: List<Release>,
    onOpenRelease: (Int) -> Unit,
    onOpenAll: () -> Unit,
) {
    val tv = isTv()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(releases.take(6), key = { it.id }) { release ->
            ReleaseCard(
                release = release,
                onClick = { onOpenRelease(release.id) },
                modifier = Modifier.width(if (tv) 170.dp else 110.dp),
            )
        }
        if (releases.size > 6) {
            item(key = "watch_all") {
                WatchAllCard(onClick = onOpenAll, modifier = Modifier.width(if (tv) 170.dp else 110.dp))
            }
        }
    }
}

@Composable
private fun WatchAllCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .background(AltBackground)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = ThirdText,
            modifier = Modifier.size(30.dp),
        )
        Text(
            text = "Смотреть всё",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MainText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = MainText,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
    )
}
