package ru.anidesk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.ui.theme.BrightSun
import ru.anidesk.app.ui.theme.CompletedBlue
import ru.anidesk.app.ui.theme.DroppedRed
import ru.anidesk.app.ui.theme.HoldOnOrange
import ru.anidesk.app.ui.theme.PlanPurple
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.WatchingGreen

fun releaseStatusColor(release: Release): Color {
    return when (release.profileListStatus) {
        1 -> WatchingGreen
        2 -> PlanPurple
        3 -> CompletedBlue
        4 -> HoldOnOrange
        5 -> DroppedRed
        else -> Color.Transparent
    }
}

@Composable
fun PosterPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
fun ReleaseCard(
    release: Release,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showInfo: Boolean = true,
    posterAspectRatio: Float = 2f / 3f,
    titleOverlay: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.then(
            if (onLongClick != null) {
                Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
            } else {
                Modifier.clickable(onClick = onClick)
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(posterAspectRatio)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            PosterPlaceholder(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp)),
            )
            AsyncImage(
                model = release.image,
                contentDescription = release.titleRu,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (release.profileListStatus != null && release.profileListStatus > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(releaseStatusColor(release)),
                )
            }
            if (titleOverlay) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            )
                        )
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = release.titleRu,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (showInfo) {
            Column(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                Text(
                    text = release.titleRu,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = Format.getEpisodeString(release),
                        fontSize = 12.sp,
                        color = SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = " • ",
                        fontSize = 12.sp,
                        color = SecondaryText,
                    )
                    Text(
                        text = String.format("%.1f", release.grade),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryText,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun ReleaseListItem(
    release: Release,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(86.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            PosterPlaceholder(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp)),
            )
            AsyncImage(
                model = release.image,
                contentDescription = release.titleRu,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (release.profileListStatus != null && release.profileListStatus > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(releaseStatusColor(release)),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = release.titleRu,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = Format.getEpisodeString(release),
                    fontSize = 13.sp,
                    color = SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = " • ", fontSize = 13.sp, color = SecondaryText)
                Text(
                    text = String.format("%.1f", release.grade),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText,
                )
            }
            if (release.isFavorite) {
                Text(
                    text = "★ Избранное",
                    fontSize = 12.sp,
                    color = BrightSun,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (release.description.isNotEmpty()) {
                Text(
                    text = release.description,
                    fontSize = 12.sp,
                    color = SecondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
