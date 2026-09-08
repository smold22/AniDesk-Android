package ru.anidesk.app.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun isTv(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
}

@Composable
fun rememberTvFocusRequester(): FocusRequester = remember { FocusRequester() }