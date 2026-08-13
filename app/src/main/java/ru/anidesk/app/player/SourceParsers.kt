package ru.anidesk.app.player

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Парсеры видеоисточников (порт anixartjs/kodikwrapper LinkParser на Kotlin/OkHttp).
 * Возвращает карту: качество -> прямой URL (HLS).
 */
object SourceParsers {

    private const val TAG = "SourceParsers"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun parse(episodeUrl: String, sourceName: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val result = try {
                when (sourceName) {
                    "Kodik" -> kodik(episodeUrl)
                    "Libria", "Liberty" -> anilibria(episodeUrl)
                    "Sibnet" -> {
                        val link = sibnet(episodeUrl) ?: return@withContext emptyMap()
                        mapOf("720" to link)
                    }
                    else -> emptyMap()
                }
            } catch (e: Exception) {
                Log.e(TAG, "parse failed for source=$sourceName url=$episodeUrl", e)
                emptyMap()
            }
            Log.i(TAG, "source=$sourceName url=$episodeUrl -> ${result.keys}")
            result
        }

    private fun fetch(url: String): String? =
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() }
        }.getOrNull()

    // ---------- Kodik ----------

    private val kodikUrlRegex = Regex("""/([a-z]+)/(\d+)/([0-9a-z]+)/(\d+p)(?:.*)""")

    private fun kodik(url: String): Map<String, String> {
        val match = kodikUrlRegex.find(url) ?: return emptyMap()
        val type = match.groupValues[1]
        val id = match.groupValues[2]
        val hash = match.groupValues[3]
        val host = runCatching { url.toHttpUrl().host }.getOrNull() ?: "kodikplayer.com"

        // Актуальный способ: GET /ftor?type=..&id=..&hash=.. (без urlParams-подписей).
        val builder = "https://$host/ftor".toHttpUrl().newBuilder()
        builder.addQueryParameter("type", type)
        builder.addQueryParameter("id", id)
        builder.addQueryParameter("hash", hash)

        val response = runCatching {
            client.newCall(Request.Builder().url(builder.build()).header("Referer", "").build())
                .execute()
        }.getOrNull()

        if (response != null && response.header("Content-Type")?.contains("application/json") == true) {
            response.use { it.body?.string() }?.let { body ->
                return parseKodikLinks(body)
            }
        }

        // Запасной способ (старый): подписи urlParams со страницы эпизода.
        return kodikLegacy(url, host)
    }

    private fun kodikLegacy(url: String, host: String): Map<String, String> {
        val page = fetch(url) ?: return emptyMap()

        fun find(regex: Regex): String? = regex.find(page)?.groupValues?.get(1)

        val urlParamsRaw = find(Regex("""var\s+urlParams\s*=\s*'([^']*)'""")) ?: return emptyMap()
        val hash = find(Regex("""\w+\.hash\s*=\s*'([^']*)'""")) ?: return emptyMap()
        val id = find(Regex("""\w+\.id\s*=\s*'([^']*)'""")) ?: return emptyMap()
        val type = find(Regex("""\w+\.type\s*=\s*'([^']*)'""")) ?: return emptyMap()

        val urlParams = runCatching { json.parseToJsonElement(urlParamsRaw).jsonObject }
            .getOrNull() ?: Json.parseToJsonElement("{}").jsonObject

        val builder: HttpUrl.Builder = "https://$host/ftor".toHttpUrl().newBuilder()
        builder.addQueryParameter("type", type)
        builder.addQueryParameter("hash", hash)
        builder.addQueryParameter("id", id)
        for ((key, value) in urlParams) {
            if (value is JsonPrimitive) builder.addQueryParameter(key, value.content)
        }

        val response = runCatching {
            client.newCall(Request.Builder().url(builder.build()).header("Referer", "").build())
                .execute()
        }.getOrNull() ?: return emptyMap()

        response.use {
            val contentType = it.header("Content-Type") ?: ""
            if (!contentType.contains("application/json")) return emptyMap()
            val body = it.body?.string().orEmpty()
            return parseKodikLinks(body)
        }
    }

    private fun parseKodikLinks(body: String): Map<String, String> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return emptyMap()

        val links = root["links"]?.jsonObject ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        for ((quality, sourcesJson) in links) {
            val array = sourcesJson.jsonArray
            if (array.isEmpty()) continue
            val src = array[0].jsonObject["src"]?.jsonPrimitive?.contentOrNull ?: continue
            val url = if (isPlainKodikUrl(src)) src else decryptKodik(src)
            if (url.isEmpty()) continue
            result[quality] = if (url.startsWith("//")) "https:$url" else url
        }
        return result
    }

    private fun isPlainKodikUrl(src: String): Boolean {
        val regex =
            Regex("""//(get|cloud)\.(kodik-storage|solodcdn)\.com/useruploads/.*?/.*?/(240|360|480|720|1080)\.mp4:hls:manifest\.m3u8""")
        return regex.containsMatchIn(src)
    }

    private fun decryptKodik(src: String): String {
        val shifted = src.map { ch ->
            when (ch) {
                in 'A'..'Z' -> ((ch.code - 'A'.code + 18) % 26 + 'A'.code).toChar()
                in 'a'..'z' -> ((ch.code - 'a'.code + 18) % 26 + 'a'.code).toChar()
                else -> ch
            }
        }.joinToString("")
        return runCatching {
            Base64.decode(shifted, Base64.DEFAULT).toString(Charsets.UTF_8)
        }.getOrDefault("")
    }

    // ---------- Sibnet ----------

    private fun sibnet(url: String): String? {
        val page = fetch(url) ?: return null
        val match = Regex("""src:\s*(".*?")""").find(page) ?: return null
        val path = match.groupValues[1].replace("\"", "")
        val full = if (path.startsWith("http")) path else "https://video.sibnet.ru$path"

        return runCatching {
            val response = client.newCall(
                Request.Builder()
                    .url(full)
                    .header("Host", "video.sibnet.ru")
                    .header("Referer", url)
                    .build()
            ).execute()
            response.use {
                if (it.isSuccessful) it.request.url.toString() else null
            }
        }.getOrNull()
    }

    // ---------- AniLibria / Liberty ----------

    private fun anilibria(url: String): Map<String, String> {
        val id = Regex("""id=(\d+)""").find(url)?.groupValues?.get(1) ?: return emptyMap()
        val episode = Regex("""ep=(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull()
            ?: return emptyMap()

        val body = fetch("https://aniliberty.top/api/v1/anime/releases/$id") ?: return emptyMap()
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return emptyMap()
        val episodes = root["episodes"]?.jsonArray ?: return emptyMap()

        val target = episodes.firstOrNull {
            it.jsonObject["ordinal"]?.jsonPrimitive?.intOrNull == episode
        } ?: return emptyMap()

        val obj = target.jsonObject
        return buildMap {
            for ((quality, key) in listOf("1080" to "hls_1080", "720" to "hls_720", "480" to "hls_480")) {
                obj[key]?.jsonPrimitive?.contentOrNull?.let { put(quality, it) }
            }
        }
    }
}
