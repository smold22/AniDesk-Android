package ru.anidesk.app.downloads

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.anidesk.app.core.network.AnixartApi
import ru.anidesk.app.core.network.Episode
import ru.anidesk.app.core.network.Release
import ru.anidesk.app.core.settings.SettingsStore
import ru.anidesk.app.player.SourceParsers
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Скачивание эпизодов в папку Download/Anime/{название аниме}
 * (MediaStore, Android 10+; на старых версиях — public Downloads/Anime/{название аниме}
 * с разрешением WRITE_EXTERNAL_STORAGE).
 *
 * Поддерживаются прямые MP4 и HLS (плейлист → сегменты, склейка в .mp4:
 * MPEG-TS или fMP4 init+segments — играется плеером).
 *
 * Загрузку можно остановить через [stop]: прерывается сетевой запрос,
 * недокачанный файл удаляется, состояние возвращается в [State.Idle].
 */
object EpisodeDownloader {

    sealed interface State {
        data object Idle : State
        data class Downloading(val progress: Float) : State
        data object Done : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states

    private val jobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    private const val TAG = "EpisodeDownloader"

    fun key(releaseId: Int, sourceId: Int, position: Int) = "$releaseId:$sourceId:$position"

    fun stateOf(key: String): State = _states.value[key] ?: State.Idle

    fun isDownloading(key: String): Boolean = stateOf(key) is State.Downloading

    fun download(
        context: Context,
        settingsStore: SettingsStore,
        api: AnixartApi,
        release: Release,
        dubberId: Int,
        sourceId: Int,
        sourceName: String,
        episode: Episode,
    ) {
        val key = key(release.id, sourceId, episode.position)
        if (isDownloading(key)) return
        _states.update { it + (key to State.Downloading(0f)) }
        val job = scope.launch {
            try {
                val uri = downloadEpisode(
                    context = context,
                    api = api,
                    release = release,
                    dubberId = dubberId,
                    sourceId = sourceId,
                    sourceName = sourceName,
                    episode = episode,
                    key = key,
                ) { progress ->
                    // Не воскрешаем остановленную загрузку прогресс-обновлением.
                    _states.update { map ->
                        if (map[key] is State.Downloading) map + (key to State.Downloading(progress)) else map
                    }
                }
                settingsStore.setEpisodeDownloaded(key, uri.toString())
                _states.update { it + (key to State.Done) }
            } catch (e: CancellationException) {
                _states.update { it + (key to State.Idle) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "download failed key=$key", e)
                _states.update { it + (key to State.Failed(e.message ?: "Ошибка загрузки")) }
            } finally {
                jobs.remove(key)
                activeCalls.remove(key)
            }
        }
        jobs[key] = job
    }

    /** Останавливает активную загрузку и удаляет недокачанный файл. */
    fun stop(key: String) {
        val job = jobs.remove(key) ?: return
        activeCalls.remove(key)?.cancel()
        job.cancel()
        _states.update { it + (key to State.Idle) }
    }

    suspend fun deleteDownloaded(
        context: Context,
        settingsStore: SettingsStore,
        key: String,
    ) {
        val uri = settingsStore.removeEpisodeDownloaded(key)
        if (uri != null) {
            runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) }
        }
        _states.update { it + (key to State.Idle) }
    }

    /** Удаляет все скачанные серии тайтла и саму папку Download/Anime/{название}. */
    suspend fun deleteAll(
        context: Context,
        settingsStore: SettingsStore,
        release: Release,
        keys: Collection<String>,
    ) {
        for (key in keys) {
            _states.update { it + (key to State.Idle) }
        }
        settingsStore.removeDownloadedForRelease(release.id)
        val folder = sanitizeFolder(release.titleRu)
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                context.contentResolver.delete(
                    collection,
                    "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                    arrayOf("Download/Anime/$folder/%"),
                )
            }
        } else {
            runCatching {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Anime/$folder",
                ).deleteRecursively()
            }
        }
    }

    // ---------- internals ----------

    private suspend fun downloadEpisode(
        context: Context,
        api: AnixartApi,
        release: Release,
        dubberId: Int,
        sourceId: Int,
        sourceName: String,
        episode: Episode,
        key: String,
        onProgress: (Float) -> Unit,
    ): Uri {
        // Прокси-парсер (baproxy) нестабилен: битые токены (404/403) и лимиты запросов.
        // Повторяем парсинг целиком с нарастающей паузой — свежий токен обычно работает.
        var lastError: Exception? = null
        repeat(6) { attempt ->
            try {
                coroutineContext.ensureActive()
                val links = SourceParsers.parse(episode.url, sourceName)
                val target = pickTarget(links)
                val fileName = "${release.titleRu} серия ${episode.position}"
                val folder = sanitizeFolder(release.titleRu)
                val directMp4 = kodikDirectMp4(target)
                if (directMp4 != null) {
                    try {
                        return downloadFile(context, key, directMp4, folder, fileName, onProgress)
                    } catch (e: Exception) {
                        if (!coroutineContext.isActive) throw CancellationException("stopped", e)
                        Log.w(TAG, "Прямой mp4 не скачался, пробуем HLS", e)
                    }
                }
                // baproxy hlsp-прокси ломается (403/404): JWT внутри содержит настоящий URL и Origin.
                val (mediaUrl, origin) = resolveProxyHls(target)
                return if (mediaUrl.contains(".m3u8")) {
                    downloadHls(context, key, mediaUrl, folder, fileName, origin, onProgress)
                } else {
                    downloadFile(context, key, mediaUrl, folder, fileName, onProgress)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!coroutineContext.isActive) throw CancellationException("stopped", e)
                lastError = e
                Log.w(TAG, "download attempt ${attempt + 1} failed", e)
                if (attempt < 5) delay(3000L * (attempt + 1))
            }
        }
        throw lastError ?: error("Ошибка загрузки")
    }

    /**
     * Kodik отдаёт прямые прогрессивные mp4: у ссылки вида
     * «.../720.mp4:hls:manifest.m3u8» достаточно отрезать HLS-суффикс —
     * получается полный mp4 всего эпизода (так скачивает официальное приложение Anixart).
     */
    private fun kodikDirectMp4(url: String): String? {
        val suffix = ":hls:manifest.m3u8"
        if (!url.endsWith(suffix)) return null
        val host = runCatching { java.net.URL(url).host }.getOrNull() ?: return null
        if (!host.contains("kodik-storage") && !host.contains("solodcdn") && !host.contains("okcdn")) return null
        return url.removeSuffix(suffix)
    }

    /**
     * hlsp-прокси baproxy часто отдаёт 403/404, хотя JWT внутри ссылки содержит
     * настоящий URL потока и требуемые заголовки (Origin). Декодируем JWT
     * и качаем поток напрямую.
     */
    private fun resolveProxyHls(url: String): Pair<String, String?> {
        val match = Regex("""https?://[^/]+/hlsp/([^/]+)/index\.m3u8""").find(url) ?: return url to null
        val token = match.groupValues[1]
        val payloadPart = token.split(".").getOrNull(1) ?: return url to null
        val decoded = runCatching {
            Base64.decode(
                payloadPart.replace('-', '+').replace('_', '/'),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
            ).toString(Charsets.UTF_8)
        }.getOrNull() ?: return url to null
        val obj = runCatching { json.parseToJsonElement(decoded).jsonObject }.getOrNull()
            ?: return url to null
        val real = obj["url"]?.jsonPrimitive?.contentOrNull ?: return url to null
        val origin = obj["headers"]?.jsonObject?.get("origin")?.jsonPrimitive?.contentOrNull
        return real to origin
    }

    /**
     * Выбор ссылки для скачивания:
     * 1) прямая медиа-ссылка (mp4/окcdn) — самый надёжный вариант;
     * 2) master-плейлист (auto) — единственный рабочий вариант у [Anixora] Alloha
     *    (quality-ссылки там отдают 404);
     * 3) quality-ссылка максимального качества (Kodik и т.п.).
     */
    private fun pickTarget(links: Map<String, String>): String {
        val master = links["auto"]
        val direct = links.entries
            .filter { it.key != "auto" }
            .sortedByDescending { it.key.filter(Char::isDigit).toIntOrNull() ?: 0 }
            .map { it.value }
            .firstOrNull { isDirectMedia(it) }
        val bestQuality = links.entries
            .filter { it.key != "auto" }
            .maxByOrNull { it.key.filter(Char::isDigit).toIntOrNull() ?: 0 }
            ?.value
        return direct
            ?: master
            ?: bestQuality
            ?: error("Нет ссылок для скачивания")
    }

    private fun isDirectMedia(url: String): Boolean {
        val parsed = runCatching { java.net.URL(url) }.getOrNull() ?: return false
        val host = parsed.host
        val path = parsed.path.orEmpty()
        return path.endsWith(".mp4") || host.contains("okcdn")
    }

    private fun downloadFile(
        context: Context,
        key: String,
        url: String,
        folder: String,
        fileName: String,
        onProgress: (Float) -> Unit,
    ): Uri {
        val builder = Request.Builder().url(url)
        if (!isProxyHost(url)) {
            builder.header("User-Agent", UA)
            builder.header("Referer", runCatching { java.net.URL(url).protocol + "://" + java.net.URL(url).host + "/" }.getOrDefault(""))
            builder.header("Origin", runCatching { java.net.URL(url).protocol + "://" + java.net.URL(url).host }.getOrDefault(""))
        }
        val call = trackCall(key, client.newCall(builder.build()))
        call.execute().use { response ->
            check(response.isSuccessful) { "Ошибка сервера: HTTP ${response.code}" }
            val total = response.body?.contentLength() ?: -1L
            val input = response.body?.byteStream() ?: error("Пустой ответ")
            return writeToMediaStore(context, folder, fileName, "mp4", "video/mp4", input, total, onProgress)
        }
    }

    private suspend fun downloadHls(
        context: Context,
        key: String,
        masterUrl: String,
        folder: String,
        fileName: String,
        origin: String? = null,
        onProgress: (Float) -> Unit,
    ): Uri {
        val master = fetch(key, masterUrl, origin) ?: error("Не удалось получить плейлист")
        val mediaUrl = resolveMediaPlaylist(masterUrl, master)
        val media = if (mediaUrl == masterUrl) master else fetch(key, mediaUrl, origin) ?: error("Не удалось получить плейлист серий")

        val lines = media.lines()
        val segments = mutableListOf<String>()
        var init: String? = null
        for (i in lines.indices) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-MAP")) {
                init = Regex("""URI="([^"]+)"""").find(line)?.groupValues?.get(1)
            } else if (line.startsWith("#EXTINF") && i + 1 < lines.size) {
                val u = lines[i + 1].trim()
                if (u.isNotEmpty() && !u.startsWith("#")) segments += u
            }
        }
        check(segments.isNotEmpty()) { "В плейлисте нет сегментов" }

        fun resolve(u: String): String =
            if (u.startsWith("http")) u else java.net.URL(java.net.URL(masterUrl), u).toString()

        val tmp = File(context.cacheDir, "download_${System.currentTimeMillis()}.ts")
        var totalBytes = 0L
        tmp.outputStream().use { out ->
            init?.let { url ->
                coroutineContext.ensureActive()
                val bytes = fetchBytes(key, resolve(url), origin) ?: error("Не удалось загрузить init-сегмент")
                out.write(bytes)
                totalBytes += bytes.size
            }
            for (i in segments.indices) {
                coroutineContext.ensureActive()
                val bytes = fetchBytes(key, resolve(segments[i]), origin) ?: error("Сегмент ${i + 1} не загрузился")
                out.write(bytes)
                totalBytes += bytes.size
                onProgress((i + 1).toFloat() / segments.size)
            }
            out.flush()
        }

        return if (init != null) {
            // fMP4 (init + сегменты) — уже MP4, сохраняем как есть.
            try {
                tmp.inputStream().use { input ->
                    writeToMediaStore(context, folder, fileName, "mp4", "video/mp4", input, totalBytes) {
                        onProgress(0.9f + 0.1f * it)
                    }
                }
            } finally {
                runCatching { tmp.delete() }
            }
        } else {
            // MPEG-TS — ремакс в настоящий MP4 (MediaExtractor + MediaMuxer).
            val remuxed = remuxTsToMp4(tmp)
            try {
                if (remuxed != null) {
                    remuxed.inputStream().use { input ->
                        writeToMediaStore(context, folder, fileName, "mp4", "video/mp4", input, remuxed.length()) {
                            onProgress(0.9f + 0.1f * it)
                        }
                    }
                } else {
                    Log.w(TAG, "remux failed, saving as .ts")
                    tmp.inputStream().use { input ->
                        writeToMediaStore(context, folder, fileName, "ts", "video/mp2t", input, totalBytes) {
                            onProgress(0.9f + 0.1f * it)
                        }
                    }
                }
            } finally {
                runCatching { remuxed?.delete() }
                runCatching { tmp.delete() }
            }
        }
    }

    /** Склейка MPEG-TS в MP4 без перекодирования (собственный парсер Media3). */
    private fun remuxTsToMp4(input: File): File? = TsRemuxer.remux(input)

    private fun resolveMediaPlaylist(masterUrl: String, master: String): String {
        if (!master.contains("#EXT-X-STREAM-INF")) return masterUrl
        val lines = master.lines()
        var bestUrl: String? = null
        var bestRes = 0
        for (i in lines.indices) {
            if (!lines[i].startsWith("#EXT-X-STREAM-INF")) continue
            val res = Regex("""RESOLUTION=(\d+)x(\d+)""").find(lines[i])
                ?.groupValues?.get(2)?.toIntOrNull() ?: 0
            val url = lines.getOrNull(i + 1)?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("#") }
            if (url != null && (bestUrl == null || res > bestRes)) {
                bestUrl = url
                bestRes = res
            }
        }
        return bestUrl?.let { if (it.startsWith("http")) it else java.net.URL(java.net.URL(masterUrl), it).toString() }
            ?: masterUrl
    }

    private suspend fun fetch(key: String, url: String, origin: String? = null): String? =
        fetchBytes(key, url, origin)?.toString(Charsets.UTF_8)

    /**
     * Скачивание с ретраями. Прокси (baproxy/hlsp) периодически меняет требования к
     * заголовкам: сначала пробуем вариант «без заголовков» для прокси-хостов
     * (и с заголовками для остальных), при неудаче — противоположный вариант.
     * [origin] — Origin-заголовок, требуемый потоком (декодируется из JWT прокси).
     */
    private suspend fun fetchBytes(key: String, url: String, origin: String? = null): ByteArray? {
        val preferHeaders = origin != null || !isProxyHost(url)
        repeat(2) { headerRound ->
            val withHeaders = if (headerRound == 0) preferHeaders else !preferHeaders
            repeat(3) { attempt ->
                coroutineContext.ensureActive()
                val result = runCatching {
                    val builder = Request.Builder().url(url)
                    if (withHeaders) {
                        if (origin != null) {
                            builder.header("Origin", origin)
                            builder.header("User-Agent", UA)
                        } else {
                            builder.header("User-Agent", UA)
                            builder.header("Referer", runCatching { java.net.URL(url).protocol + "://" + java.net.URL(url).host + "/" }.getOrDefault(""))
                            builder.header("Origin", runCatching { java.net.URL(url).protocol + "://" + java.net.URL(url).host }.getOrDefault(""))
                        }
                    }
                    trackCall(key, client.newCall(builder.build())).execute().use { response ->
                        if (!response.isSuccessful) {
                            if (attempt == 2 && headerRound == 1) {
                                Log.e(TAG, "GET failed url=$url status=${response.code}")
                            }
                            null
                        } else {
                            response.body?.bytes()
                        }
                    }
                }.getOrNull()
                if (result != null) return result
                if (!coroutineContext.isActive) throw CancellationException("stopped")
                if (attempt < 2) delay(1500L * (attempt + 1))
            }
        }
        return null
    }

    /** Прокси (baproxy/hlsp) отдаёт 403, если в запросе есть Referer/Origin/UA. */
    private fun isProxyHost(url: String): Boolean {
        val host = runCatching { java.net.URL(url).host }.getOrNull() ?: return false
        return host.contains("ds1nc.ru") || host.contains("baproxy")
    }

    private fun trackCall(key: String, call: Call): Call {
        activeCalls[key] = call
        return call
    }

    /** Название папки аниме без недопустимых в имени файла символов. */
    private fun sanitizeFolder(title: String): String {
        val cleaned = title
            .replace(Regex("""[\\/:*?"<>|]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('.')
        return cleaned.takeIf { it.isNotEmpty() }?.take(80)?.trimEnd() ?: "Anime"
    }

    private fun writeToMediaStore(
        context: Context,
        folder: String,
        fileName: String,
        extension: String,
        mime: String,
        input: java.io.InputStream,
        total: Long,
        onProgress: (Float) -> Unit,
    ): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.$extension")
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Anime/$folder")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
                ?: error("Не удалось создать файл в папке $folder")
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    copyStream(input, out, total, onProgress)
                }
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
            } catch (e: Exception) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw e
            }
            return uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Anime/$folder")
            check(dir.exists() || dir.mkdirs()) { "Не удалось создать папку $folder" }
            val file = File(dir, "$fileName.$extension")
            try {
                file.outputStream().use { out ->
                    copyStream(input, out, total, onProgress)
                }
            } catch (e: Exception) {
                runCatching { file.delete() }
                throw e
            }
            return Uri.fromFile(file)
        }
    }

    private fun copyStream(input: java.io.InputStream, out: java.io.OutputStream, total: Long, onProgress: (Float) -> Unit) {
        val buf = ByteArray(64 * 1024)
        var done = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            done += n
            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
        }
        out.flush()
    }
}