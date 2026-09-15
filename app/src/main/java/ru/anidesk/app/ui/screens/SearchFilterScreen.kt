package ru.anidesk.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Type
import ru.anidesk.app.ui.theme.Carmine
import ru.anidesk.app.ui.theme.MainText
import java.util.Calendar

data class SearchFilter(
    val sort: Int = 0,
    val statusId: Int? = null,
    val categoryId: Int? = null,
    val country: String? = null,
    val startYear: Int? = null,
    val endYear: Int? = null,
    val season: Int? = null,
    val genres: List<String> = emptyList(),
    val types: List<Int> = emptyList(),
    val ageRatings: List<Int> = emptyList(),
) {
    val isEmpty: Boolean
        get() = statusId == null && categoryId == null && country == null &&
            startYear == null && endYear == null && season == null &&
            genres.isEmpty() && types.isEmpty() && ageRatings.isEmpty() && sort == 0
}

object SearchFilterOptions {

    val CATEGORIES = listOf(1 to "Сериал", 2 to "Фильм", 3 to "OVA")
    val STATUSES = listOf(1 to "Завершенные", 2 to "Онгоинги", 3 to "Анонсы")
    val SEASONS = listOf(1 to "Зима", 2 to "Весна", 3 to "Лето", 4 to "Осень")
    val AGE_RATINGS = listOf(0 to "0+", 6 to "6+", 16 to "16+", 18 to "18+")
    val SORTS = listOf(
        0 to "По дате обновления",
        1 to "По оценке",
        2 to "По году",
        3 to "По популярности",
    )
    val COUNTRIES = listOf(
        "Япония",
        "Китай",
        "Корея Южная",
        "Корея Северная",
        "США",
        "Россия",
        "Франция",
        "Германия",
        "Великобритания",
        "Канада",
        "Австралия",
        "Италия",
        "Испания",
        "Тайвань",
        "Гонконг",
        "Индия",
        "Бразилия",
    )
    val GENRES = listOf(
        "комедия",
        "драма",
        "фэнтези",
        "экшен",
        "приключения",
        "романтика",
        "сёнен",
        "сёдзё",
        "сэйнэн",
        "дзёсэй",
        "ужасы",
        "мистика",
        "триллер",
        "детектив",
        "научная фантастика",
        "меха",
        "повседневность",
        "школа",
        "спорт",
        "музыка",
        "исэкай",
        "гарем",
        "этти",
        "боевые искусства",
        "исторический",
        "психологическое",
    )

    val YEARS: List<Int> = (Calendar.getInstance().get(Calendar.YEAR) downTo 1990).toList()
}

@Composable
fun SearchFilterScreen(
    api: AnixartApi,
    initial: SearchFilter,
    onApply: (SearchFilter) -> Unit,
    onClose: () -> Unit,
) {
    PhoneChipFilter(
        api = api,
        initial = initial,
        onApply = onApply,
        onClose = onClose,
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PhoneChipFilter(
    api: AnixartApi,
    initial: SearchFilter,
    onApply: (SearchFilter) -> Unit,
    onClose: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    var types by remember { mutableStateOf<List<Type>>(emptyList()) }
    var showAllTypes by remember { mutableStateOf(false) }
    var showAllYears by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        types = runCatching { api.types() }.getOrDefault(emptyList())
    }

    BackHandler(onBack = onClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
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
                    .clickable(onClick = onClose)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text(
                "Фильтры",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MainText,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            TvFilterSection("Сортировка") {
                TvChipFlow {
                    SearchFilterOptions.SORTS.forEach { (id, label) ->
                        TvSelectableRow(label, draft.sort == id, { draft = draft.copy(sort = if (draft.sort == id) 0 else id) })
                    }
                }
            }
            TvFilterSection("Категория") {
                TvChipFlow {
                    SearchFilterOptions.CATEGORIES.forEach { (id, label) ->
                        TvSelectableRow(label, draft.categoryId == id, { draft = draft.copy(categoryId = if (draft.categoryId == id) null else id) })
                    }
                }
            }
            TvFilterSection("Страна") {
                TvChipFlow {
                    SearchFilterOptions.COUNTRIES.forEach { name ->
                        TvSelectableRow(name, draft.country == name, { draft = draft.copy(country = if (draft.country == name) null else name) })
                    }
                }
            }
            TvFilterSection("Год") {
                TvChipFlow {
                    val visibleYears = if (showAllYears) SearchFilterOptions.YEARS else SearchFilterOptions.YEARS.take(6)
                    visibleYears.forEach { year ->
                        TvSelectableRow(
                            "с $year",
                            draft.startYear == year,
                            { draft = draft.copy(startYear = if (draft.startYear == year) null else year) },
                        )
                        TvSelectableRow(
                            "по $year",
                            draft.endYear == year,
                            { draft = draft.copy(endYear = if (draft.endYear == year) null else year) },
                        )
                    }
                    if (SearchFilterOptions.YEARS.size > 6 && !showAllYears) {
                        TvSelectableRow("Все", false, { showAllYears = true })
                    }
                    if (showAllYears && SearchFilterOptions.YEARS.size > 6) {
                        TvSelectableRow("Свернуть", false, { showAllYears = false })
                    }
                }
            }
            TvFilterSection("Озвучка") {
                TvChipFlow {
                    val visibleTypes = if (showAllTypes) types else types.take(6)
                    visibleTypes.forEach { type ->
                        TvSelectableRow(
                            type.name,
                            type.id in draft.types,
                            {
                                draft = draft.copy(
                                    types = if (type.id in draft.types) draft.types - type.id else draft.types + type.id
                                )
                            },
                        )
                    }
                    if (types.size > 6 && !showAllTypes) {
                        TvSelectableRow("Все", false, { showAllTypes = true })
                    }
                    if (showAllTypes && types.size > 6) {
                        TvSelectableRow("Свернуть", false, { showAllTypes = false })
                    }
                }
            }
            TvFilterSection("Статус") {
                TvChipFlow {
                    SearchFilterOptions.STATUSES.forEach { (id, label) ->
                        TvSelectableRow(label, draft.statusId == id, { draft = draft.copy(statusId = if (draft.statusId == id) null else id) })
                    }
                }
            }
            TvFilterSection("Сезон") {
                TvChipFlow {
                    SearchFilterOptions.SEASONS.forEach { (id, label) ->
                        TvSelectableRow(label, draft.season == id, { draft = draft.copy(season = if (draft.season == id) null else id) })
                    }
                }
            }
            TvFilterSection("Возрастной рейтинг") {
                TvChipFlow {
                    SearchFilterOptions.AGE_RATINGS.forEach { (id, label) ->
                        TvSelectableRow(
                            label,
                            id in draft.ageRatings,
                            {
                                draft = draft.copy(
                                    ageRatings = if (id in draft.ageRatings) draft.ageRatings - id else draft.ageRatings + id
                                )
                            },
                        )
                    }
                }
            }
            TvFilterSection("Жанры") {
                TvChipFlow {
                    SearchFilterOptions.GENRES.forEach { genre ->
                        TvSelectableRow(
                            genre,
                            genre in draft.genres,
                            {
                                draft = draft.copy(
                                    genres = if (genre in draft.genres) draft.genres - genre else draft.genres + genre
                                )
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = { draft = SearchFilter() }, modifier = Modifier.weight(1f)) {
                Text("Сбросить", fontSize = 15.sp)
            }
            TextButton(onClick = { onApply(draft) }, modifier = Modifier.weight(1f)) {
                Text("Применить", color = Carmine, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


@Composable
private fun TvFilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MainText,
        )
        content()
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TvChipFlow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun TvSelectableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val backgroundColor = when {
        focused -> Carmine
        selected -> Carmine.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    val contentColor = when {
        focused -> Color.White
        selected -> Carmine
        else -> MainText
    }
    Row(
        modifier = modifier
            .clip(shape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Carmine
                    selected -> Carmine.copy(alpha = 0.6f)
                    else -> MainText.copy(alpha = 0.25f)
                },
                shape = shape,
            )
            .background(backgroundColor, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
