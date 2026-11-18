package ru.anidesk.app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Клиент API Anixart (порт anixartjs на Kotlin/Ktor).
 * Токен передаётся query-параметром `token`.
 *
 * Если выбранный хост не отвечает (таймаут, 5xx, обрыв соединения), запрос
 * автоматически повторяется на резервных зеркалах. Рабочий хост запоминается
 * через [onHostChanged], чтобы следующие запросы шли сразу на него.
 */
class AnixartApi(
    baseUrl: String = DEFAULT_BASE_URL,
    private val onHostChanged: ((String) -> Unit)? = null,
) {
    @Volatile
    var baseUrl: String = normalizeBaseUrl(baseUrl)
        set(value) {
            field = normalizeBaseUrl(value)
        }

    var token: String? = null

    private val client = HttpClient(OkHttp) {
        // Иначе HTTP 5xx (например, 504 от нестабильного прокси) парсится
        // в пустой ответ и экран показывает "ничего не найдено".
        expectSuccess = true

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
        install(HttpRequestRetry) {
            maxRetries = 1
            // Повторяем только безопасные запросы: повтор POST на создание
            // подборки/релиза мог бы создать дубликат.
            retryIf { request, response ->
                response.status.value in 500..599 && request.allowsServerErrorRetry()
            }
            retryOnExceptionIf { _, cause ->
                cause is ConnectException || cause is UnknownHostException || cause is NoRouteToHostException
            }
            exponentialDelay(base = 2.0, baseDelayMs = 500, maxDelayMs = 2_000, randomizationMs = 200)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 45_000
            connectTimeoutMillis = 10_000
            // Без явного значения OkHttp использует 10 с: прокси успевает
            // отдать 504 только через ~15 с, и мы зря считаем это обрывом.
            // 18 с — компромисс: 504 от прокси ещё успевает прийти, а зависший
            // запрос не блокирует экран на полминуты.
            socketTimeoutMillis = 18_000
        }
        engine {
            addInterceptor(
                HostFallbackInterceptor(
                    currentBaseUrl = { baseUrl },
                    onHostChanged = ::switchToHost,
                )
            )
        }
    }

    private fun switchToHost(base: String) {
        val normalized = normalizeBaseUrl(base)
        if (baseUrl == normalized) return
        baseUrl = normalized
        onHostChanged?.invoke(normalized)
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

    suspend fun signInWithVk(vkAccessToken: String): LoginResponse {
        val form = listOf("vkAccessToken" to vkAccessToken)
        val response = client.post("$baseUrl/auth/vk") {
            header("User-Agent", USER_AGENT)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(form.formUrlEncode())
        }
        return response.body()
    }

    suspend fun signUpWithVk(
        login: String,
        email: String,
        vkAccessToken: String,
    ): SignUpResponse {
        val form = listOf(
            "login" to login,
            "email" to email,
            "vkAccessToken" to vkAccessToken,
        )
        val response = client.post("$baseUrl/auth/vk") {
            header("User-Agent", USER_AGENT)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(form.formUrlEncode())
        }
        return response.body()
    }

    suspend fun verifyVk(
        login: String,
        email: String,
        vkAccessToken: String,
        hash: String,
        code: String,
    ): VerifyResponse {
        val form = listOf(
            "login" to login,
            "email" to email,
            "vkAccessToken" to vkAccessToken,
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

    /**
     * Серверный фид уведомлений. Публичные методы принимают страницу с 1,
     * но API нумерует страницы с 0 (как related/filter/history).
     */
    suspend fun notificationEpisodes(page: Int): PageableResponse<EpisodeNotification> =
        apiGet("/notification/episodes/${page - 1}")

    suspend fun notificationRelatedReleases(page: Int): PageableResponse<RelatedReleaseNotification> =
        apiGet("/notification/related/release/${page - 1}")

    suspend fun notificationFriends(page: Int): PageableResponse<FriendNotification> =
        apiGet("/notification/friends/${page - 1}")

    suspend fun notificationReleaseComments(page: Int): PageableResponse<ReleaseCommentNotification> =
        apiGet("/notification/releaseComments/${page - 1}")

    suspend fun notificationCollectionComments(page: Int): PageableResponse<CollectionCommentNotification> =
        apiGet("/notification/collectionComments/${page - 1}")

    suspend fun notificationMyCollectionComments(page: Int): PageableResponse<MyCollectionCommentNotification> =
        apiGet("/notification/myCollectionComments/${page - 1}")

    suspend fun readNotifications() = apiGet<Unit>("/notification/read")

    suspend fun removeAllNotifications() = apiGet<Unit>("/notification/delete/all")

    // ---------- Notification preferences ----------

    suspend fun notificationPreferences(): NotificationPreferenceResponse =
        apiGet("/profile/preference/notification/my")

    /** Переключает настройку. Типы: episode, episode/first, related/release, comment и т.д. */
    suspend fun editNotificationPreference(type: String) =
        apiGet<Unit>("/profile/preference/notification/$type/edit")

    suspend fun editNotificationStatusPreferences(statuses: List<Int>) =
        apiPostJson<Unit>(
            "/profile/preference/notification/status/edit",
            body = NotificationStatusPreferencesRequest(statuses),
        )

    suspend fun editNotificationTypePreferences(types: List<Int>) =
        apiPostJson<Unit>(
            "/profile/preference/notification/type/edit",
            body = NotificationTypePreferencesRequest(types),
        )

    suspend fun popularTypeIds(): List<Int> =
        apiGet<TypesResponse>("/type/all").popularTypeIds

    // ---------- Collections ----------

    suspend fun collections(page: Int): PageableResponse<UserCollection> =
        apiGet("/collection/all/$page")

    suspend fun createCollection(
        title: String,
        description: String = "",
        isPrivate: Boolean = false,
        releases: List<Int> = emptyList(),
    ): CollectionResponse = apiPostJson(
        "/collectionMy/create",
        body = CreateCollectionRequest(
            title = title,
            description = description,
            isPrivate = isPrivate,
            releases = releases,
        ),
    )

    suspend fun updateCollection(
        id: Int,
        title: String,
        description: String = "",
        isPrivate: Boolean = false,
        releases: List<Int> = emptyList(),
    ): CollectionResponse = apiPostJson(
        "/collectionMy/edit/$id",
        body = CreateCollectionRequest(
            title = title,
            description = description,
            isPrivate = isPrivate,
            releases = releases,
        ),
    )

    suspend fun collection(id: Int): UserCollection? =
        apiGet<CollectionResponse>("/collection/$id").collection

    suspend fun uploadCollectionImage(
        id: Int,
        bytes: ByteArray,
        fileName: String = "cover.jpg",
        mimeType: String = "image/jpeg",
    ): EditImageResponse {
        val body = formData {
            append(
                "image",
                bytes,
                Headers.build {
                    append(HttpHeaders.ContentType, mimeType)
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                },
            )
            append("name", "image")
        }
        val response = client.post("$baseUrl/collectionMy/editImage/$id") {
            header("User-Agent", USER_AGENT)
            token?.let { url.parameters.append("token", it) }
            setBody(MultiPartFormDataContent(body))
        }
        return response.body()
    }

    suspend fun collectionReleases(id: Int, page: Int): PageableResponse<Release> =
        apiGet("/collection/$id/releases/$page")

    suspend fun collectionReleaseAdd(id: Int, releaseId: Int): CodeResponse =
        apiGet("/collectionMy/release/add/$id", mapOf("release_id" to releaseId.toString()))

    suspend fun deleteCollection(id: Int): CodeResponse =
        apiGet("/collectionMy/delete/$id")

    suspend fun profileCollections(id: Int, page: Int): PageableResponse<UserCollection> =
        apiGet("/collection/all/profile/$id/$page")

    // ---------- Internals ----------

    /**
     * Пробует запрос на выбранном хосте, затем на резервных зеркалах.
     *
     * OkHttp-перехватчик, а не отдельный слой API: все методы уже строят URL
     * от [baseUrl], поэтому достаточно подменить host в готовом запросе.
     */
    private class HostFallbackInterceptor(
        private val currentBaseUrl: () -> String,
        private val onHostChanged: (String) -> Unit,
    ) : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val requestBase = "${request.url.scheme}://${request.url.host}"
            val targets = buildList {
                add(requestBase)
                add(currentBaseUrl())
                addAll(FALLBACK_BASE_URLS)
            }.mapNotNull { it.toHttpUrlOrNull() }
                .distinctBy { "${it.scheme}://${it.host}:${it.port}" }

            if (targets.size <= 1) return chain.proceed(request)

            val oneShotBody = request.body?.isOneShot() == true
            var lastError: IOException? = null

            for ((index, target) in targets.withIndex()) {
                if (chain.call().isCanceled()) break

                val candidate = request.newBuilder()
                    .url(
                        request.url.newBuilder()
                            .scheme(target.scheme)
                            .host(target.host)
                            .port(target.port)
                            .build()
                    )
                    .build()

                try {
                    val response = chain.proceed(candidate)
                    val retryServerError = response.code in 500..599 &&
                        !oneShotBody &&
                        index < targets.lastIndex &&
                        request.allowsServerErrorRetry()
                    if (retryServerError) {
                        response.close()
                        continue
                    }
                    if (response.isSuccessful) {
                        val base = "${target.scheme}://${target.host}"
                        if (base != currentBaseUrl()) onHostChanged(base)
                    }
                    return response
                } catch (error: IOException) {
                    lastError = error
                    val canRetry = !chain.call().isCanceled() &&
                        !oneShotBody &&
                        index < targets.lastIndex &&
                        request.allowsIoErrorRetry(error)
                    if (!canRetry) throw error
                }
            }
            throw lastError ?: IOException("Запрос отменён")
        }
    }

    private suspend inline fun <reified T> apiGet(path: String, params: Map<String, String> = emptyMap()): T {
        val response = client.get("$baseUrl$path") {
            header("User-Agent", USER_AGENT)
            token?.let { url.parameters.append("token", it) }
            params.forEach { (key, value) -> url.parameters.append(key, value) }
        }
        return response.body()
    }

    private suspend inline fun <reified T> apiPost(path: String, params: Map<String, String> = emptyMap()): T {
        val response = client.post("$baseUrl$path") {
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

        const val DEFAULT_BASE_URL = "https://api.anixsekai.com"

        /** Зеркала API в порядке приоритета для автопереключения. */
        private val FALLBACK_BASE_URLS = listOf(
            DEFAULT_BASE_URL,
            "https://api.anixart.app",
        )

        fun normalizeBaseUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return DEFAULT_BASE_URL
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }
    }

    /** Освобождает сетевые ресурсы (например, после разового использования в воркере). */
    fun close() {
        client.close()
    }
}

/** Повторять на резервном хосте при 5xx можно только безопасные запросы. */
private fun Request.allowsServerErrorRetry(): Boolean = when (method) {
    "GET", "HEAD" -> true
    "POST" -> url.encodedPath.contains("/filter") || url.encodedPath.contains("/search")
    else -> false
}

/**
 * Повтор при обрыве соединения. Для POST разрешаем только ошибки, при которых
 * запрос точно не дошёл до сервера (иначе создание подборки может продублироваться).
 */
private fun Request.allowsIoErrorRetry(error: IOException): Boolean = when (method) {
    "GET", "HEAD" -> true
    else -> error is ConnectException ||
        error is UnknownHostException ||
        error is NoRouteToHostException ||
        error is SSLException
}

/** Повтор при 5xx на уровне Ktor (после того как отработал fallback по хостам). */
private fun HttpRequest.allowsServerErrorRetry(): Boolean = when (method) {
    HttpMethod.Get, HttpMethod.Head -> true
    HttpMethod.Post -> url.encodedPath.contains("/filter") || url.encodedPath.contains("/search")
    else -> false
}
