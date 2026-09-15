package ru.anidesk.app.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** GitHub-репозиторий, из релизов которого берутся обновления. */
private const val GITHUB_OWNER = "smold22"
private const val GITHUB_REPO = "AniDesk-Android"

/** Информация о доступном обновлении из GitHub Releases. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long?,
    val changelog: String,
    val apkUrl: String,
    val apkSize: Long,
    val releaseUrl: String,
)

/** Состояние проверки/загрузки обновления. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus

    /** [percent] == -1, если размер ответа неизвестен. */
    data class Downloading(val percent: Int) : UpdateStatus
    data class Ready(val file: File) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

/**
 * Проверка обновлений через GitHub Releases API, загрузка APK
 * и запуск системного установщика через FileProvider.
 */
class UpdateManager(private val appContext: Context) {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    /** Версия, для которой нужно показать диалог «доступно обновление». */
    private val _promptVersion = MutableStateFlow<String?>(null)
    val promptVersion: StateFlow<String?> = _promptVersion.asStateFlow()

    private val _installPrompt = MutableStateFlow(false)
    val installPrompt: StateFlow<Boolean> = _installPrompt.asStateFlow()

    /** Нужно показать диалог с просьбой разрешить установку из неизвестных источников. */
    private val _permissionPrompt = MutableStateFlow(false)
    val permissionPrompt: StateFlow<Boolean> = _permissionPrompt.asStateFlow()

    /** Файл, ожидающий установки после выдачи разрешения. */
    private var pendingInstall: File? = null

    private var downloadJob: Job? = null

    private val installedVersionCode: Long by lazy {
        runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode
        }.getOrDefault(0L)
    }

    private val installedVersionName: String by lazy {
        runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()
        }.getOrDefault("0")
    }

    private fun apkFile(): File {
        val dir = File(appContext.cacheDir, "apk")
        dir.mkdirs()
        return File(dir, "update.apk")
    }

    /** Проверяет последний релиз на GitHub. */
    fun checkForUpdates() {
        if (_status.value == UpdateStatus.Checking) return
        _status.value = UpdateStatus.Checking
        scope.launch {
            try {
                val info = fetchLatestRelease()
                _status.value = if (info == null) UpdateStatus.UpToDate else UpdateStatus.Available(info)
                if (info != null) {
                    _promptVersion.value = info.versionName
                }
            } catch (e: Exception) {
                _status.value = UpdateStatus.Failed(friendlyError(e))
            }
        }
    }

    /** Прячет диалог обновления. */
    fun dismissPrompt() {
        _promptVersion.value = null
    }

    /** Скачивает APK из релиза с прогрессом в [status]. */
    fun download(info: UpdateInfo) {
        if (downloadJob?.isActive == true) return
        val target = apkFile()
        target.delete()
        downloadJob = scope.launch {
            try {
                val response = client.get(info.apkUrl)
                if (!response.status.isSuccess()) {
                    throw IOException("HTTP ${response.status.value}")
                }
                val total = response.contentLength() ?: 0L
                val channel = response.bodyAsChannel()
                FileOutputStream(target).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastPercent = -2
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        val percent = if (total > 0) {
                            ((downloaded * 100) / total).toInt()
                        } else {
                            -1
                        }
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _status.value = UpdateStatus.Downloading(percent)
                        }
                    }
                }
                if (!target.exists() || target.length() == 0L) {
                    throw IOException("Файл обновления пуст")
                }
                _status.value = UpdateStatus.Ready(target)
                _installPrompt.value = true
            } catch (e: Exception) {
                target.delete()
                _status.value = UpdateStatus.Failed(friendlyError(e))
            }
        }
    }

    fun dismissInstallPrompt() {
        _installPrompt.value = false
    }

    fun dismissFailed() {
        if (_status.value is UpdateStatus.Failed) _status.value = UpdateStatus.Idle
    }

    /** Запускает системный установщик для скачанного APK. */
    fun install(file: File) {
        if (!file.exists()) {
            _status.value = UpdateStatus.Failed("Файл обновления не найден")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstall = file
            _permissionPrompt.value = true
            return
        }
        doInstall(file)
    }

    /** Открывает экран «Установка из неизвестных источников» для приложения. */
    fun openInstallSourceSettings() {
        _permissionPrompt.value = false
        runCatching {
            appContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${appContext.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            _status.value = UpdateStatus.Failed("Не удалось открыть разрешения: ${it.message}")
        }
    }

    fun dismissPermissionPrompt() {
        _permissionPrompt.value = false
    }

    /** После возврата из настроек пытается установить отложенный APK. */
    fun retryPendingInstall() {
        val file = pendingInstall ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            return
        }
        pendingInstall = null
        doInstall(file)
    }

    private fun doInstall(file: File) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(intent) }
            .onFailure { _status.value = UpdateStatus.Failed("Не удалось открыть установщик: ${it.message}") }
    }

    private suspend fun fetchLatestRelease(): UpdateInfo? {
        val response = client.get("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest") {
            header("Accept", "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) {
            throw IOException("HTTP ${response.status.value}")
        }
        val json = Json { ignoreUnknownKeys = true }
        val release = json.decodeFromString<GitHubRelease>(response.bodyAsText())
        if (release.draft || release.prerelease) return null
        val tag = release.tagName
        if (!isNewer(tag)) return null
        val apk = release.assets.firstOrNull {
            it.name.endsWith(".apk", ignoreCase = true)
        } ?: return null
        return UpdateInfo(
            versionName = parseVersionName(tag),
            versionCode = parseVersionCode(tag),
            changelog = release.body.trim(),
            apkUrl = apk.downloadUrl,
            apkSize = apk.size,
            releaseUrl = release.htmlUrl,
        )
    }

    private fun isNewer(tag: String): Boolean {
        val remoteCode = parseVersionCode(tag)
        if (remoteCode != null) return remoteCode > installedVersionCode
        return compareVersionNames(parseVersionName(tag), installedVersionName) > 0
    }

    private fun friendlyError(e: Throwable): String = when {
        e is IOException && e.message?.contains("HTTP 403") == true ->
            "Превышен лимит запросов к GitHub. Повторите позже."
        e is IOException && e.message?.contains("HTTP 404") == true ->
            "Релиз не найден в репозитории GitHub."
        e is IOException ->
            "Ошибка сети: ${e.message ?: "нет соединения"}"
        else ->
            "Ошибка: ${e.message ?: "неизвестная ошибка"}"
    }

    private companion object {
        private val versionCodeRegex = Regex("""\((\d+)\)""")

        /** Извлекает код версии из тега вида "v1.3.3 (133)". */
        fun parseVersionCode(tag: String): Long? =
            versionCodeRegex.find(tag)?.groupValues?.get(1)?.toLongOrNull()

        /** Извлекает номер версии из тега: "v1.3.3" -> "1.3.3". */
        fun parseVersionName(tag: String): String =
            Regex("""\d+(\.\d+)*""").find(tag)?.value.orEmpty()

        fun compareVersionNames(a: String, b: String): Int {
            val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
            val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrElse(i) { 0 }
                val y = pb.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
)