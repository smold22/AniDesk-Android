package ru.anidesk.app.ui.screens

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

internal const val NETWORK_UNAVAILABLE_MESSAGE = "Нет интернета или включён VPN"
internal const val SERVER_TIMEOUT_MESSAGE = "Сервер не ответил вовремя. Попробуйте ещё раз"
internal const val SERVER_UNAVAILABLE_MESSAGE = "Сервер временно недоступен. Попробуйте позже"
internal const val SECURE_CONNECTION_MESSAGE = "Ошибка защищённого соединения. Проверьте VPN или прокси"

/** Сервер не ответил за отведённое время (таймаут запроса/сокета). */
internal fun Throwable.isTimeoutProblem(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 12) {
        if (current is HttpRequestTimeoutException ||
            current is ConnectTimeoutException ||
            current is SocketTimeoutException ||
            current is java.net.SocketTimeoutException
        ) {
            return true
        }
        val text = current.message.orEmpty()
        if (text.contains("timeout", ignoreCase = true) ||
            text.contains("timed out", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
        depth++
    }
    return false
}

/** Сервер ответил 5xx (например, 504 Gateway Timeout от прокси). */
internal fun Throwable.isServerUnavailableProblem(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 12) {
        if (current is ServerResponseException) return true
        if (current is ResponseException && current.response.status.value in 500..599) return true
        val text = current.message.orEmpty()
        if (text.contains("Gateway Timeout", ignoreCase = true) ||
            text.contains("Service Unavailable", ignoreCase = true) ||
            text.contains("Bad Gateway", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
        depth++
    }
    return false
}

/** Проблема TLS: сертификат, handshake, перехват трафика VPN/прокси. */
internal fun Throwable.isSecureConnectionProblem(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 12) {
        if (current is SSLException ||
            current is CertificateException ||
            current is CertPathValidatorException
        ) {
            return true
        }
        val text = current.message.orEmpty()
        if (text.contains("Trust anchor", ignoreCase = true) ||
            text.contains("CertPathValidator", ignoreCase = true) ||
            text.contains("certification path", ignoreCase = true) ||
            text.contains("SSLHandshake", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
        depth++
    }
    return false
}

/** Похоже на отсутствие сети/VPN: DNS, отказ в соединении, разрыв. */
internal fun Throwable.isNetworkOrVpnProblem(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 12) {
        if (current is UnknownHostException ||
            current is ConnectException ||
            current is NoRouteToHostException
        ) {
            return true
        }
        val text = current.message.orEmpty()
        if (text.contains("Network is unreachable", ignoreCase = true) ||
            text.contains("No route to host", ignoreCase = true) ||
            text.contains("Connection refused", ignoreCase = true) ||
            text.contains("Connection reset", ignoreCase = true) ||
            text.contains("Failed to connect", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
        depth++
    }
    return false
}

internal fun Throwable.networkAwareMessage(prefix: String? = null): String {
    when {
        isTimeoutProblem() -> return SERVER_TIMEOUT_MESSAGE
        isServerUnavailableProblem() -> return SERVER_UNAVAILABLE_MESSAGE
        this is ServerResponseException -> return SERVER_UNAVAILABLE_MESSAGE
        this is ClientRequestException -> return "Ошибка запроса (HTTP ${response.status.value})"
        this is ResponseException -> return "Сервер вернул ошибку (HTTP ${response.status.value})"
        isSecureConnectionProblem() -> return SECURE_CONNECTION_MESSAGE
        isNetworkOrVpnProblem() -> return NETWORK_UNAVAILABLE_MESSAGE
    }
    val details = message?.sanitizeToken()
    return when {
        prefix.isNullOrBlank() -> details ?: NETWORK_UNAVAILABLE_MESSAGE
        details.isNullOrBlank() -> prefix
        else -> "$prefix $details"
    }
}

/** Не показываем токен авторизации, даже если он попал в текст ошибки. */
private fun String.sanitizeToken(): String =
    replace(Regex("([?&]token=)[^&\\s]+"), "$1***")
