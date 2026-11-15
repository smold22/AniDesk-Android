package ru.anidesk.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import ru.anidesk.app.R
import ru.anidesk.app.ui.components.TabHeader
import ru.anidesk.app.ui.theme.MainText
import ru.anidesk.app.ui.theme.SecondaryText
import ru.anidesk.app.ui.theme.ThirdText

private data class DonateOption(
    val title: String,
    val subtitle: String,
    val copyText: String,
)

private val DONATE_OPTIONS = listOf(
    DonateOption(
        title = "Халва",
        subtitle = "Номер карты: 2200271132125221",
        copyText = "2200271132125221",
    ),
    DonateOption(
        title = "Сбер",
        subtitle = "Номер карты: 2202208864624896",
        copyText = "2202208864624896",
    ),
    DonateOption(
        title = "Газпромбанк",
        subtitle = "Номер карты: 2200012779564912",
        copyText = "2200012779564912",
    ),
    DonateOption(
        title = "ВТБ",
        subtitle = "Номер карты: 2200240721059216",
        copyText = "2200240721059216",
    ),
    DonateOption(
        title = "ЮMoney",
        subtitle = "Номер кошелька: 4100118897580208",
        copyText = "4100118897580208",
    ),
)

@Composable
fun DonateScreen(navController: NavHostController) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        TabHeader("Донаты", onBack = { navController.popBackStack() })
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "AniDesk бесплатен и без рекламы. Если он вам нравится — поддержите разработку любым удобным способом. Нажмите на способ, чтобы скопировать номер карты или кошелька.",
                fontSize = 14.sp,
                color = SecondaryText,
                modifier = Modifier.padding(16.dp),
            )

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))

            DONATE_OPTIONS.forEachIndexed { index, option ->
                if (index > 0) SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Paid,
                    title = option.title,
                    summary = option.subtitle,
                    onClick = {
                        copyToClipboard(context, option)
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Скопировать",
                            tint = ThirdText,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            val catComposition = rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.cat))
            LottieAnimation(
                composition = catComposition.value,
                modifier = Modifier.fillMaxWidth(),
                iterations = Int.MAX_VALUE,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Спасибо за поддержку!",
                fontSize = 15.sp,
                color = MainText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            )
        }
    }
}

private fun copyToClipboard(context: Context, option: DonateOption) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(option.title, option.copyText))
    Toast.makeText(context, "${option.title}: номер скопирован", Toast.LENGTH_SHORT).show()
}
