package ru.anidesk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.ui.theme.MainText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object Format {
    val seasons = listOf(null, "Зима", "Весна", "Лето", "Осень")

    fun getAgeRate(rate: Int): String = when (rate) {
        2 -> "6+"
        3 -> "12+"
        4 -> "16+"
        5 -> "18+"
        else -> "0+"
    }

    fun getEpisodeString(release: Release): String {
        val released = release.episodesReleased ?: 0
        val total = release.episodesTotal
        return when {
            total == null && released == 0 -> "?"
            total == null -> released.toString()
            total == 0 -> released.toString()
            released == total -> total.toString()
            else -> "$released из $total"
        }
    }

    private val historyTimeFormat = SimpleDateFormat("d MMM yyyy 'в' HH:mm", Locale("ru"))
    private val historyDateFormat = SimpleDateFormat("d MMM 'в' HH:mm", Locale("ru"))
    private val historyTimeOnly = SimpleDateFormat("HH:mm", Locale("ru"))

    private fun isSameDay(aSec: Long, bSec: Long): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = aSec * 1000 }
        val b = Calendar.getInstance().apply { timeInMillis = bSec * 1000 }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.MONTH) == b.get(Calendar.MONTH) &&
            a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH)
    }

    private fun isSameYear(aSec: Long, bSec: Long): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = aSec * 1000 }
        val b = Calendar.getInstance().apply { timeInMillis = bSec * 1000 }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
    }

    private fun formatHistoryTime(timestampSec: Long, pattern: SimpleDateFormat): String =
        pattern.format(timestampSec * 1000)

    fun getHistoryTime(timestamp: Long): String {
        val now = System.currentTimeMillis() / 1000
        val diff = now - timestamp
        val days = diff / 86400
        val hours = (diff / 3600) % 24
        val minutes = (diff / 60) % 60
        val seconds = diff % 60
        val sameToday = isSameDay(now, timestamp)
        val sameYesterday = isSameDay(now - 86400, timestamp)
        val sameYear = isSameYear(now, timestamp)
        return when {
            days == 0L && hours == 0L && minutes == 0L && seconds < 5 -> "только что"
            days == 0L && hours == 0L && minutes == 0L && seconds > 5 -> "$seconds сек назад"
            days == 0L && hours == 0L && minutes >= 1 -> "$minutes мин назад"
            days == 0L && hours in 1..3 -> "$hours ч назад"
            days == 0L && hours >= 4 && sameToday ->
                "сегодня в ${formatHistoryTime(timestamp, historyTimeOnly)}"
            days == 0L && hours >= 4 && !sameToday && sameYesterday ->
                "вчера в ${formatHistoryTime(timestamp, historyTimeOnly)}"
            days == 1L && sameYesterday ->
                "вчера в ${formatHistoryTime(timestamp, historyTimeOnly)}"
            days == 1L && !sameYesterday ->
                formatHistoryTime(timestamp, historyDateFormat)
            days >= 2L && sameYear ->
                formatHistoryTime(timestamp, historyDateFormat)
            else -> formatHistoryTime(timestamp, historyTimeFormat)
        }
    }
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
    }
}

@Composable
fun TabHeader(title: String, onBack: () -> Unit) {
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
