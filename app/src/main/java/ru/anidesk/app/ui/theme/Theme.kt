package ru.anidesk.app.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ---- Статические акценты (одинаковы в обеих темах; палитра WarmAmber из YummyTV) ----
val Carmine = Color(0xFFFFB86B)
val OnCarmine = Color(0xFF241100)
val PlayerRed = Carmine
val Blue = Color(0xFF5D68A1)
val BrightSun = Color(0xFFFFD740)
val Lavender = Color(0xFFE8DEF7)
val WatchingGreen = Color(0xE666BB6C)
val PlanPurple = Color(0xE6B566BB)
val CompletedBlue = Color(0xE65C6CC0)
val HoldOnOrange = Color(0xE6FFCA28)
val DroppedRed = Color(0xE6EF5450)

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
    background = Color(0xFF0B0D10),
    altBackground = Color(0xFF14171C),
    backgroundTertiary = Color(0xFF1E242B),
    mainText = Color(0xFFF4F1EA),
    secondaryText = Color(0xFFC9C2B8),
    thirdText = Color(0xFF8E877D),
    selectButton = Color(0xFF1E242B),
    navBarBackground = Color(0xFF14171C),
    separator = Color(0x0DF4F1EA),
    border = Color(0x1AF4F1EA),
)

private val LightAniColors = AniColors(
    background = Color(0xFFFFFFFF),
    altBackground = Color(0xFFF0F0F0),
    backgroundTertiary = Color(0xFFE6E6E6),
    mainText = Color(0xFF1A1A1A),
    secondaryText = Color(0xFF444444),
    thirdText = Color(0xFF757575),
    selectButton = Color(0xFFF0F0F0),
    navBarBackground = Color(0xFFFFFFFF),
    separator = Color(0x0D1A1A1A),
    border = Color(0x1A1A1A),
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
    onPrimary = OnCarmine,
    primaryContainer = Carmine.copy(alpha = 0.18f),
    onPrimaryContainer = Color(0xFFFFE0BA),
    secondary = Color(0xFFD8C3A5),
    onSecondary = Color(0xFF3A2E1A),
    tertiary = Lavender,
    onTertiary = Color(0xFF121212),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFF4F1EA),
    surface = Color(0xFF14171C),
    onSurface = Color(0xFFF4F1EA),
    surfaceVariant = Color(0xFF1E242B),
    onSurfaceVariant = Color(0xFFC9C2B8),
    outline = Color(0xFF8E877D),
    outlineVariant = Color(0x2AF4F1EA),
    surfaceContainerLowest = Color(0xFF08090C),
    surfaceContainerLow = Color(0xFF101318),
    surfaceContainer = Color(0xFF1E242B),
    surfaceContainerHigh = Color(0xFF2A2D33),
    surfaceContainerHighest = Color(0xFF323640),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB05A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC0),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF7A5A2E),
    onSecondary = Color.White,
    tertiary = Lavender,
    onTertiary = Color(0xFF3A2E4A),
    background = Color.White,
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFF0F0F0),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF444444),
    outline = Color(0xFF757575),
    outlineVariant = Color(0x1A1A1A1A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE0E0E0),
)

private val AniShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
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
            shapes = AniShapes,
            typography = AniMobileTypography,
        ) {
            content()
        }
    }
}
