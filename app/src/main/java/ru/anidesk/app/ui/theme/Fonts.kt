package ru.anidesk.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.anidesk.app.R

val OutfitFontFamily = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_bold, FontWeight.Bold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
)

private fun outfit(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = OutfitFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

/** Компактная шкала для сенсорных устройств. */
val AniMobileTypography = Typography(
    displayLarge = outfit(57, 64, FontWeight.ExtraBold),
    displayMedium = outfit(45, 52, FontWeight.ExtraBold),
    displaySmall = outfit(36, 44, FontWeight.Bold),
    headlineLarge = outfit(32, 40, FontWeight.Bold),
    headlineMedium = outfit(28, 36, FontWeight.Bold),
    headlineSmall = outfit(24, 32, FontWeight.Bold),
    titleLarge = outfit(22, 28, FontWeight.Bold),
    titleMedium = outfit(16, 24, FontWeight.Bold),
    titleSmall = outfit(14, 20, FontWeight.Bold),
    bodyLarge = outfit(16, 24),
    bodyMedium = outfit(14, 20),
    bodySmall = outfit(12, 16),
    labelLarge = outfit(14, 20, FontWeight.Bold),
    labelMedium = outfit(12, 16, FontWeight.Bold),
    labelSmall = outfit(11, 16),
)