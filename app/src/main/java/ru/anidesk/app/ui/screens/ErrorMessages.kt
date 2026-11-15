package ru.anidesk.app.ui.screens

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

internal const val NETWORK_UNAVAILABLE_MESSAGE = "Нет интернета или включён VPN"

internal fun Throwable.isNetworkOrVpnProblem(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 12) {
        if (current is UnknownHostException ||
            current is ConnectException ||
            current is SocketTimeoutException ||
            current is NoRouteToHostException ||
            current is SSLException ||
            current is CertificateException ||
            current is CertPathValidatorException
        ) {
            return true
        }
        val text = current.message.orEmpty()
        if (text.contains("Trust anchor", ignoreCase = true) ||
            text.contains("CertPathValidator", ignoreCase = true) ||
            text.contains("certification path", ignoreCase = true) ||
            text.contains("SSLHandshake", ignoreCase = true) ||
            text.contains("Network is unreachable", ignoreCase = true) ||
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
    if (isNetworkOrVpnProblem()) return NETWORK_UNAVAILABLE_MESSAGE
    val details = message
    return when {
        prefix.isNullOrBlank() -> details ?: NETWORK_UNAVAILABLE_MESSAGE
        details.isNullOrBlank() -> prefix
        else -> "$prefix $details"
    }
}
