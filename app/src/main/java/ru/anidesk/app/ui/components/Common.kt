package ru.anidesk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anidesk.app.core.network.Release

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
