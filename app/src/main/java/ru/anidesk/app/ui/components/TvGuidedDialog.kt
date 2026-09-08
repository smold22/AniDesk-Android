package ru.anidesk.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.ThirdText

data class TvGuidedItem(
    val id: Long,
    val title: String,
    val description: String? = null,
    val enabled: Boolean = true,
)

/** Guided-диалог в стиле AniTV (Leanback GuidedStep): список с D-pad, выделенный элемент. */
@Composable
fun TvGuidedDialog(
    title: String,
    description: String? = null,
    items: List<TvGuidedItem>,
    selectedId: Long? = null,
    onSelect: (Long) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.5f)
                .padding(start = 56.dp, end = 32.dp),
        ) {
            Text(
                text = title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (description != null) {
                Text(
                    text = description,
                    fontSize = 18.sp,
                    color = Color(0xFFB0B0B0),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        val listState = rememberLazyListState()
        val selectedIndex = items.indexOfFirst { it.id == selectedId }
        LaunchedEffect(selectedId, items.size) {
            if (selectedIndex >= 0) listState.scrollToItem(selectedIndex)
        }
        val firstItemFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(150)
            firstItemFocus.requestFocus()
        }
        var focusedId by remember { mutableStateOf<Long?>(null) }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.4f)
                .padding(end = 48.dp)
                .heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val isSelected = item.id == selectedId
                val isFocused = focusedId == item.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                isSelected -> Carmine.copy(alpha = 0.3f)
                                isFocused -> Color.White.copy(alpha = 0.12f)
                                else -> Color.Transparent
                            }
                        )
                        .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                        .focusable(item.enabled)
                        .onFocusChanged { if (it.isFocused) focusedId = item.id }
                        .clickable(enabled = item.enabled) { onSelect(item.id) }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Carmine,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontSize = 18.sp,
                            color = if (isSelected) Carmine else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.description?.let {
                            Text(
                                text = it,
                                fontSize = 14.sp,
                                color = ThirdText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}