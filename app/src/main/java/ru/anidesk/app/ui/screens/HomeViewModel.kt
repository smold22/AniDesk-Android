package ru.anidesk.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.anidesk.app.AniDeskApplication
import ru.anidesk.app.core.network.PageableResponse
import ru.anidesk.app.core.network.Release

internal sealed interface HomeTab {
    val title: String

    data class Filter(
        override val title: String,
        val sort: Int = 0,
        val statusId: Int? = null,
        val categoryId: Int? = null,
        val country: String? = null,
    ) : HomeTab

    data object Schedule : HomeTab {
        override val title = "Расписание"
    }
}

internal val HOME_TABS = listOf<HomeTab>(
    HomeTab.Filter("Аниме", country = "Япония"),
    HomeTab.Filter("Дунхуа", country = "Китай"),
    HomeTab.Filter("Последние", sort = 0),
    HomeTab.Filter("Онгоинги", statusId = 2),
    HomeTab.Filter("Анонсы", statusId = 3),
    HomeTab.Filter("Завершенные", statusId = 1),
    HomeTab.Filter("Фильмы", categoryId = 2),
    HomeTab.Filter("OVA", categoryId = 3),
    HomeTab.Schedule,
)

data class HomeUiState(
    val tabIndex: Int = 0,
    val releases: List<Release> = emptyList(),
    val schedule: List<Pair<String, List<Release>>> = emptyList(),
    val scheduleDay: Int = 0,
    val page: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val api = (app as AniDeskApplication).api

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        loadTab(0)
    }

    fun selectTab(index: Int) {
        if (index == _state.value.tabIndex) return
        _state.update { it.copy(tabIndex = index) }
        loadTab(index)
    }

    fun selectDay(day: Int) {
        _state.update { it.copy(scheduleDay = day) }
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached || current.releases.isEmpty()) return
        if (loadMoreJob?.isActive == true) return
        val tab = HOME_TABS.getOrNull(current.tabIndex) as? HomeTab.Filter ?: return
        val tabIndex = current.tabIndex
        val next = current.page + 1
        _state.update { it.copy(loadingMore = true) }
        loadMoreJob = viewModelScope.launch {
            var pageResult: PageableResponse<Release>? = null
            repeat(3) { attempt ->
                if (pageResult != null) return@repeat
                try {
                    pageResult = api.filterReleases(
                        page = next,
                        sort = tab.sort,
                        statusId = tab.statusId,
                        categoryId = tab.categoryId,
                        country = tab.country,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w(
                        "Pagination",
                        "Home tab=$tabIndex page=$next attempt=${attempt + 1} failed: ${e.message}",
                    )
                    if (attempt < 2) delay(1200)
                }
            }
            val res = pageResult
            if (res == null) {
                android.util.Log.w("Pagination", "Home tab=$tabIndex page=$next failed after 3 attempts")
                _state.update { if (it.tabIndex == tabIndex) it.copy(loadingMore = false) else it }
                return@launch
            }
            android.util.Log.i("Pagination", "Home tab=$tabIndex page=$next got=${res.content.size}")
            // Пока грузилась страница, пользователь мог переключить вкладку — не смешиваем списки.
            _state.update { state ->
                if (state.tabIndex != tabIndex) {
                    state
                } else {
                    state.copy(
                        releases = (state.releases + res.content).distinctBy { release -> release.id },
                        page = next,
                        endReached = res.content.isEmpty(),
                        loadingMore = false,
                    )
                }
            }
        }
    }

    fun refresh() {
        val current = _state.value
        val tab = HOME_TABS.getOrNull(current.tabIndex) ?: return
        _state.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            try {
                when (tab) {
                    is HomeTab.Filter -> {
                        val res = api.filterReleases(
                            page = 0,
                            sort = tab.sort,
                            statusId = tab.statusId,
                            categoryId = tab.categoryId,
                            country = tab.country,
                        )
                        _state.update {
                            it.copy(releases = res.content, page = 0, endReached = res.content.isEmpty())
                        }
                    }
                    HomeTab.Schedule -> {
                        _state.update { it.copy(schedule = fetchSchedule(), scheduleDay = 0) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = errorMessage(tab, e)) }
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    private fun loadTab(index: Int) {
        val tab = HOME_TABS.getOrNull(index) ?: return
        loadJob?.cancel()
        loadMoreJob?.cancel()
        loadMoreJob = null
        _state.update {
            it.copy(
                loading = true,
                loadingMore = false,
                error = null,
                releases = emptyList(),
                page = 0,
                endReached = false,
                scheduleDay = 0,
            )
        }
        loadJob = viewModelScope.launch {
            try {
                when (tab) {
                    is HomeTab.Filter -> {
                        val res = api.filterReleases(
                            page = 0,
                            sort = tab.sort,
                            statusId = tab.statusId,
                            categoryId = tab.categoryId,
                            country = tab.country,
                        )
                        _state.update {
                            it.copy(releases = res.content, endReached = res.content.isEmpty(), loading = false)
                        }
                    }
                    HomeTab.Schedule -> {
                        _state.update { it.copy(schedule = fetchSchedule(), loading = false) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = errorMessage(tab, e)) }
            }
        }
    }

    private suspend fun fetchSchedule(): List<Pair<String, List<Release>>> {
        val res = api.schedule()
        return listOf(
            "Понедельник" to res.monday,
            "Вторник" to res.tuesday,
            "Среда" to res.wednesday,
            "Четверг" to res.thursday,
            "Пятница" to res.friday,
            "Суббота" to res.saturday,
            "Воскресенье" to res.sunday,
        )
    }

    private fun errorMessage(tab: HomeTab, e: Exception): String =
        if (tab is HomeTab.Schedule) {
            e.networkAwareMessage("Не удалось загрузить расписание:")
        } else {
            e.networkAwareMessage("Не удалось загрузить:")
        }
}
