package ru.anidesk.app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Клиент API Anixart (порт anixartjs на Kotlin/Ktor).
 * Токен передаётся query-параметром `token`.
 */
class AnixartApi(
    var baseUrl: String = "https://api-s.anixsekai.com",
) {
    var token: String? = null

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                    explicitNulls = true
                    coerceInputValues = true
                }
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
    }

    // ---------- Auth ----------

    suspend fun signIn(login: String, password: String): LoginResponse {
        val form = listOf("login" to login, "password" to password)
        val response = client.post("$baseUrl/auth/signIn") {
            header("User-Agent", USER_AGENT)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(form.formUrlEncode())
        }
        return response.body()
    }

    suspend fun signUp(login: String, email: String, password: String): SignUpResponse {
        val form = listOf("login" to login, "email" to email, "password" to password)
        val response = client.post("$baseUrl/auth/signUp") {
            header("User-Agent", USER_AGENT)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(form.formUrlEncode())
        }
        return response.body()
    }

    suspend fun signUpVerify(
        login: String,
        email: String,
        password: String,
        hash: String,
        code: String,
    ): VerifyResponse {
        val form = listOf(
            "login" to login,
            "email" to email,
            "password" to password,
            "hash" to hash,
            "code" to code,
        )
        val response = client.post("$baseUrl/auth/verify") {
            header("User-Agent", USER_AGENT)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(form.formUrlEncode())
        }
        return response.body()
    }

    // ---------- Восстановление пароля ----------

    /** Шаг 1: запрос кода восстановления. Возвращает hash для подтверждения. */
    suspend fun restorePassword(login: String): SignUpResponse {
        val form = listOf("data" to login)
        val response = client.post("$baseUrl/auth/restore") {
            header("User-Agent", USER_AGENT)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(form.formUrlEncode())
        }
        return response.body()
    }

    /** Шаг 2: подтверждение кода и установка нового пароля. При code=0 возвращает профиль и токен. */
    suspend fun restorePasswordVerify(
        login: String,
        password: String,
        hash: String,
        code: String,
    ): VerifyResponse {
        val form = listOf(
            "data" to login,
            "password" to password,
            "code" to code,
            "hash" to hash,
        )
        val response = client.post("$baseUrl/auth/restore/verify") {
            header("User-Agent", USER_AGENT)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(form.formUrlEncode())
        }
        return response.body()
    }

    // ---------- Profile ----------

    suspend fun profile(id: Int): ProfileResponse = apiGet("/profile/$id")

    // ---------- Release ----------

    suspend fun releaseInfo(id: Int): ReleaseResponse =
        apiGet("/release/$id", mapOf("extended_mode" to "true"))

    /** @param page 1-индексированная страница (в API страницы начинаются с 0). */
    suspend fun relatedReleases(relatedId: Int, page: Int): PageableResponse<Release> =
        client.get("$baseUrl/related/$relatedId/${page - 1}") {
            header("User-Agent", USER_AGENT)
            header("API-Version", "v2")
            token?.let { url.parameters.append("token", it) }
        }.body()

    suspend fun filterReleases(
        page: Int,
        sort: Int = 0,
        statusId: Int? = null,
        categoryId: Int? = null,
        country: String? = null,
        startYear: Int? = null,
        endYear: Int? = null,
        season: Int? = null,
        genres: List<String> = emptyList(),
        types: List<Int> = emptyList(),
        ageRatings: List<Int> = emptyList(),
    ): PageableResponse<Release> =
        apiPostJson(
            "/filter/$page",
            query = mapOf("extended_mode" to "true"),
            body = ReleaseFilterRequest(
                sort = sort,
                statusId = statusId,
                categoryId = categoryId,
                country = country,
                startYear = startYear,
                endYear = endYear,
                season = season,
                genres = genres,
                types = types,
                ageRatings = ageRatings,
            ),
        )

    suspend fun getDubbers(releaseId: Int): DubbersResponse = apiGet("/episode/$releaseId")

    suspend fun getDubberSources(releaseId: Int, dubberId: Int): SourcesResponse =
        apiGet("/episode/$releaseId/$dubberId")

    suspend fun getEpisodes(
        releaseId: Int,
        dubberId: Int,
        sourceId: Int,
        sort: Int = 1,
    ): EpisodesResponse =
        apiGet("/episode/$releaseId/$dubberId/$sourceId", mapOf("sort" to sort.toString()))

    suspend fun markEpisodeAsWatched(releaseId: Int, sourceId: Int, position: Int) =
        apiGet<Unit>("/episode/watch/$releaseId/$sourceId/$position")

    suspend fun addToHistory(releaseId: Int, sourceId: Int, position: Int) =
        apiGet<Unit>("/history/add/$releaseId/$sourceId/$position")

    suspend fun addFavorite(releaseId: Int) = apiGet<Unit>("/favorite/add/$releaseId")

    suspend fun removeFavorite(releaseId: Int) = apiGet<Unit>("/favorite/delete/$releaseId")

    suspend fun addToProfileList(releaseId: Int, type: Int) =
        apiGet<Unit>("/profile/list/add/$type/$releaseId")

    suspend fun removeFromProfileList(releaseId: Int, type: Int) =
        apiGet<Unit>("/profile/list/delete/$type/$releaseId")

    // ---------- Feed / Schedule ----------

    suspend fun feedLatest(page: Int): PageableResponse<Article> =
        client.post("$baseUrl/article/latest/all/$page") {
            header("User-Agent", USER_AGENT)
            token?.let { url.parameters.append("token", it) }
        }.body()

    suspend fun schedule(): ScheduleResponse = apiGet("/schedule")

    // ---------- Search ----------

    suspend fun searchReleases(page: Int, query: String): List<Release> =
        client.post("$baseUrl/search/releases/$page") {
            header("User-Agent", USER_AGENT)
            header("API-Version", "v2")
            token?.let { url.parameters.append("token", it) }
            contentType(ContentType.Application.Json)
            setBody(SearchRequest(query = query))
        }.body<SearchResponse>().releases

    // ---------- History / Lists / Favorites ----------

    suspend fun history(page: Int): PageableResponse<Release> = apiGet("/history/$page")

    suspend fun deleteFromHistory(releaseId: Int) = apiGet<Unit>("/history/delete/$releaseId")

    suspend fun profileList(type: Int, page: Int, sort: Int = 1): PageableResponse<Release> =
        apiGet("/profile/list/all/$type/$page", mapOf("sort" to sort.toString()))

    suspend fun favorites(page: Int): PageableResponse<Release> = apiGet("/favorite/all/$page")

    suspend fun episodeUpdates(typeId: Int, page: Int): PageableResponse<Release> =
        apiGet("/episode/updates/$typeId/$page")

    suspend fun types(): List<Type> = apiGet<TypesResponse>("/type/all").types

    // ---------- Discover ----------

    suspend fun discoverWatching(page: Int): PageableResponse<Release> =
        apiGet("/discover/watching/$page")

    suspend fun discoverRecommendations(page: Int): PageableResponse<Release> =
        apiGet("/discover/recommendations/$page", mapOf("previous_page" to "-1"))

    // ---------- Notifications ----------

    suspend fun notificationCount(): NotificationCountResponse = apiGet("/notification/count")

    /** Серверный фид уведомлений (1-индексированные страницы, как в AnixartM). */
    suspend fun notificationEpisodes(page: Int): PageableResponse<EpisodeNotification> =
        apiGet("/notification/episodes/$page")

    suspend fun notificationRelatedReleases(page: Int): PageableResponse<RelatedReleaseNotification> =
        apiGet("/notification/related/release/$page")

    // ---------- Internals ----------

    private suspend inline fun <reified T> apiGet(path: String, params: Map<String, String> = emptyMap()): T {
        val response = client.get("$baseUrl$path") {
            header("User-Agent", USER_AGENT)
            token?.let { url.parameters.append("token", it) }
            params.forEach { (key, value) -> url.parameters.append(key, value) }
        }
        return response.body()
    }

    private suspend inline fun <reified T> apiPostJson(
        path: String,
        query: Map<String, String> = emptyMap(),
        body: Any,
    ): T {
        val response = client.post("$baseUrl$path") {
            header("User-Agent", USER_AGENT)
            token?.let { url.parameters.append("token", it) }
            query.forEach { (key, value) -> url.parameters.append(key, value) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return response.body()
    }

    companion object {
        const val USER_AGENT =
            "AnixartApp/9.0 BETA 3-25021818 (Android 9; SDK 28; x86_64; ROG ASUS AI2201_B; ru)"
    }
}
