package ru.anidesk.app.ui.theme

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import ru.anidesk.app.ui.components.FocusScaleIndication

// ---- Статические акценты (одинаковы в обеих темах, палитра Anixart 8.5.2) ----
val Carmine = Color(0xFFF04E4E)
val PlayerRed = Carmine
val Blue = Color(0xFF5D68A1)
val BrightSun = Color(0xFFFFD740)
val Lavender = Color(0xFFE8DEF7)
val WatchingGreen = Color(0xFF66BB6C)
val PlanPurple = Color(0xFF9314DC)
val CompletedBlue = Blue
val HoldOnOrange = Color(0xFFDF960E)
val DroppedRed = Color(0xFFBE1414)

// ---- Семантические цвета (свои для тёмной/светлой темы) ----
data class AniColors(
    val background: Color,
    val altBackground: Color,
    val backgroundTertiary: Color,
    val mainText: Color,
    val secondaryText: Color,
    val thirdText: Color,
    val selectButton: Color,
    val navBarBackground: Color,
    val separator: Color,
    val border: Color,
)

private val DarkAniColors = AniColors(
    background = Color(0xFF121212),
    altBackground = Color(0xFF252525),
    backgroundTertiary = Color(0xFF1A1A1A),
    mainText = Color(0xFFE0E0E0),
    secondaryText = Color(0xFF9E9E9E),
    thirdText = Color(0xFF757575),
    selectButton = Color(0xFF252525),
    navBarBackground = Color(0xFF252525),
    separator = Color(0x0DFFFFFF),
    border = Color(0x1AFFFFFF),
)

private val LightAniColors = AniColors(
    background = Color(0xFFFFFFFF),
    altBackground = Color(0xFFF7F7F7),
    backgroundTertiary = Color(0xFFEFEFEF),
    mainText = Color(0xFF212121),
    secondaryText = Color(0xFF757575),
    thirdText = Color(0xFF9E9E9E),
    selectButton = Color(0xFFF0F0F0),
    navBarBackground = Color(0xFFFFFFFF),
    separator = Color(0x0D000000),
    border = Color(0x1A000000),
)

val LocalAniColors = staticCompositionLocalOf { DarkAniColors }

// ---- Доступ из композиции (прежние имена) ----
val Background: Color @Composable get() = LocalAniColors.current.background
val AltBackground: Color @Composable get() = LocalAniColors.current.altBackground
val BackgroundTertiary: Color @Composable get() = LocalAniColors.current.backgroundTertiary
val MainText: Color @Composable get() = LocalAniColors.current.mainText
val SecondaryText: Color @Composable get() = LocalAniColors.current.secondaryText
val ThirdText: Color @Composable get() = LocalAniColors.current.thirdText
val SelectButton: Color @Composable get() = LocalAniColors.current.selectButton
val NavBarBackground: Color @Composable get() = LocalAniColors.current.navBarBackground
val Separator: Color @Composable get() = LocalAniColors.current.separator
val Border: Color @Composable get() = LocalAniColors.current.border

private val DarkColors = darkColorScheme(
    primary = Carmine,
    onPrimary = Color.White,
    primaryContainer = Carmine.copy(alpha = 0.2f),
    onPrimaryContainer = Color(0xFFFFDAD7),
    secondary = Color(0xFFE0E0E0),
    onSecondary = Color(0xFF121212),
    tertiary = Lavender,
    onTertiary = Color(0xFF121212),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF252525),
    onSurfaceVariant = Color(0xFF9E9E9E),
    outline = Color(0x1AFFFFFF),
    outlineVariant = Color(0x0DFFFFFF),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF252525),
    surfaceContainerHigh = Color(0xFF2E2E2E),
    surfaceContainerHighest = Color(0xFF383838),
)

private val LightColors = lightColorScheme(
    primary = Carmine,
    onPrimary = Color.White,
    primaryContainer = Carmine.copy(alpha = 0.15f),
    onPrimaryContainer = Color(0xFFB3271E),
    secondary = Color(0xFF212121),
    onSecondary = Color.White,
    tertiary = Lavender,
    onTertiary = Color(0xFF3A2E4A),
    background = Color.White,
    onBackground = Color(0xFF212121),
    surface = Color.White,
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFF7F7F7),
    onSurfaceVariant = Color(0xFF757575),
    outline = Color(0x1A000000),
    outlineVariant = Color(0x0D000000),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE0E0E0),
)

@Composable
fun AniDeskTheme(themeMode: Int = 0, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            window.setBackgroundDrawable(
                ColorDrawable(if (dark) DarkAniColors.background.toArgb() else LightAniColors.background.toArgb())
            )
        }
    }
    CompositionLocalProvider(
        LocalAniColors provides if (dark) DarkAniColors else LightAniColors,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
        ) {
            val isTv = LocalConfiguration.current.uiMode and
                Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
            if (isTv) {
                CompositionLocalProvider(
                    LocalIndication provides FocusScaleIndication(),
                    content = content,
                )
            } else {
                content()
            }
        }
    }
}
