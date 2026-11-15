package ru.anidesk.app.ui.auth

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import org.json.JSONObject
import ru.anidesk.app.R
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class VkOAuthActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingOverlay: View
    private var completed = false
    private lateinit var codeVerifier: String
    private lateinit var state: String
    private var cachedIconPng: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setLightSystemBars(true)

        codeVerifier = savedInstanceState?.getString(KEY_VERIFIER) ?: generateCodeVerifier()
        state = savedInstanceState?.getString(KEY_STATE) ?: UUID.randomUUID().toString()

        webView = WebView(this)
        loadingOverlay = buildLoadingOverlay()

        val root = FrameLayout(this)
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            loadingOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(root)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean = request?.url?.toString()?.let(::handleUrl) ?: false

            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                url?.let(::handleUrl) ?: false

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                url?.let(::handleUrl)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (completed) return
                val host = url?.let { Uri.parse(it).host }
                if (host == "id.vk.ru" || host == "id.vk.com") {
                    view?.evaluateJavascript(buildBrandingScript(), null)
                }
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        finish()
                    }
                }
            },
        )

        if (savedInstanceState == null) {
            webView.loadUrl(buildAuthUrl())
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun buildAuthUrl(): String = Uri.parse(AUTH_ENDPOINT)
        .buildUpon()
        .appendQueryParameter("client_id", CLIENT_ID)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("redirect_uri", REDIRECT_URI)
        .appendQueryParameter("code_challenge_method", "s256")
        .appendQueryParameter("code_challenge", codeChallenge(codeVerifier))
        .appendQueryParameter("state", state)
        .appendQueryParameter("sdk_type", "vkid")
        .appendQueryParameter("v", SDK_VERSION)
        .appendQueryParameter("scope", "email")
        .build()
        .toString()

    private fun handleUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
        Log.i(
            TAG,
            "handleUrl host=${uri.host} path=${uri.path} code=${!code.isNullOrBlank()} error=$error",
        )
        if (completed || !isRedirect(uri)) return false

        val returnedState = uri.getQueryParameter("state")
        val deviceId = uri.getQueryParameter("device_id")
        if (!error.isNullOrBlank()) {
            finishWithError(error)
            return true
        }
        if (returnedState != state || code.isNullOrBlank() || deviceId.isNullOrBlank()) {
            finishWithError("Некорректный ответ ВКонтакте")
            return true
        }

        completed = true
        setLightSystemBars(false)
        loadingOverlay.visibility = View.VISIBLE
        exchangeCode(code, deviceId)
        return true
    }

    private fun setLightSystemBars(light: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    private fun isRedirect(uri: Uri): Boolean =
        uri.scheme == REDIRECT_SCHEME && uri.host == REDIRECT_HOST && uri.path == REDIRECT_PATH

    private fun exchangeCode(code: String, deviceId: String) {
        val body = listOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "code_verifier" to codeVerifier,
            "client_id" to CLIENT_ID,
            "device_id" to deviceId,
            "redirect_uri" to REDIRECT_URI,
            "state" to state,
        ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }

        Thread {
            var token: String? = null
            var errorMessage: String? = null
            try {
                val connection = (URL(TOKEN_ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                Log.i(TAG, "token exchange status=$status bytes=${responseText.length}")
                if (status in 200..299) {
                    token = JSONObject(responseText).optString("access_token").takeIf { it.isNotBlank() }
                    if (token == null) errorMessage = "ВКонтакте не вернул токен"
                } else {
                    errorMessage = runCatching {
                        JSONObject(responseText).optString("error_description")
                            .ifBlank { JSONObject(responseText).optString("error") }
                    }.getOrNull().orEmpty().ifBlank { "Ошибка ВКонтакте ($status)" }
                    Log.i(TAG, "token exchange error=$errorMessage")
                }
            } catch (e: Exception) {
                Log.i(TAG, "token exchange failed: ${e.javaClass.simpleName}")
                errorMessage = "Ошибка сети ВКонтакте"
            }

            val resultToken = token
            val resultError = errorMessage
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val data = Intent()
                if (!resultToken.isNullOrBlank()) {
                    setResult(Activity.RESULT_OK, data.putExtra(EXTRA_ACCESS_TOKEN, resultToken))
                } else {
                    setResult(
                        Activity.RESULT_CANCELED,
                        data.putExtra(EXTRA_ERROR, resultError ?: "Не удалось войти через ВКонтакте"),
                    )
                }
                finish()
            }
        }.start()
    }

    private fun finishWithError(message: String) {
        if (completed) return
        completed = true
        val data = Intent().putExtra(EXTRA_ERROR, message)
        setResult(Activity.RESULT_CANCELED, data)
        finish()
    }

    private fun buildLoadingOverlay(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF1E1E1E.toInt())
            visibility = View.GONE
        }
        val progress = ProgressBar(this)
        layout.addView(progress)
        val label = TextView(this).apply {
            text = "Вход через ВКонтакте…"
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        layout.addView(
            label,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (16 * resources.displayMetrics.density).toInt() },
        )
        return layout
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
        outState.putString(KEY_VERIFIER, codeVerifier)
        outState.putString(KEY_STATE, state)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun appIconPng(): ByteArray = cachedIconPng ?: run {
        val drawable = ContextCompat.getDrawable(this, R.mipmap.ic_launcher_round)
            ?: ContextCompat.getDrawable(this, R.mipmap.ic_launcher)
        val bitmap = drawable?.toBitmap(192, 192, Bitmap.Config.ARGB_8888)
            ?: Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }.also { cachedIconPng = it }
    }

    private fun buildBrandingScript(): String {
        val icon = JSONObject.quote(
            "data:image/png;base64," +
                Base64.encodeToString(appIconPng(), Base64.NO_WRAP),
        )
        return """
            (function(){
                var ICON = $icon;
                var APP = 'AniDesk';
                if (window.__aniDeskReapply) { window.__aniDeskReapply(); return; }
                var names = [
                    'Аниксарт - аниме в твоём кармане',
                    'Аниксарт', 'Анихарт', 'Anixart', 'anixart', 'AniChart'
                ];
                function rebrand(value){
                    var result = value;
                    for (var i = 0; i < names.length; i++) {
                        result = result.split(names[i]).join(APP);
                    }
                    result = result.replace(
                        /(Вход|Войти)\s+в\s+«?["']?AniDesk["']?»?/g,
                        APP
                    );
                    result = result.replace(
                        /(Sign in|Log in|Continue)\s+to\s+"?AniDesk"?/gi,
                        APP
                    );
                    return result;
                }
                function walk(root){
                    if (!root) return;
                    var walker = document.createTreeWalker(
                        root, NodeFilter.SHOW_TEXT, null, false
                    );
                    var node;
                    while (walker.nextNode()) {
                        var current = walker.currentNode;
                        var text = current.nodeValue || '';
                        var changed = rebrand(text);
                        if (changed !== text) current.nodeValue = changed;
                    }
                }
                function iconTargets(){
                    var init = window.init;
                    var app = init && init.data && init.data.app;
                    var urls = [];
                    if (app) {
                        app.name = APP;
                        app.name_accuse = APP;
                        if (app.photo) urls.push(app.photo.split('?')[0]);
                        if (app.photo_24) urls.push(app.photo_24.split('?')[0]);
                    }
                    return urls;
                }
                function swapIcons(){
                    var urls = iconTargets();
                    var nodes = document.querySelectorAll(
                        'img, [style*="background-image"]'
                    );
                    for (var i = 0; i < nodes.length; i++) {
                        var el = nodes[i];
                        var src = el.getAttribute('src') || el.getAttribute('srcset') || '';
                        var style = el.getAttribute('style') || '';
                        var base = src.split('?')[0];
                        var hit = false;
                        for (var j = 0; j < urls.length; j++) {
                            if (urls[j] && (base.indexOf(urls[j]) === 0 || style.indexOf(urls[j]) !== -1)) {
                                hit = true;
                                break;
                            }
                        }
                        if (!hit && /vkuserphoto\.ru/.test(src) && /size=150x150/.test(src)) {
                            hit = true;
                        }
                        if (!hit) continue;
                        if (el.tagName === 'IMG') {
                            if (el.getAttribute('src') !== ICON) {
                                el.removeAttribute('srcset');
                                el.setAttribute('src', ICON);
                                el.style.objectFit = 'cover';
                            }
                        } else if (style.indexOf(ICON) === -1) {
                            el.style.backgroundImage = 'url("' + ICON + '")';
                            el.style.backgroundSize = 'cover';
                        }
                    }
                }
                function apply(){
                    walk(document.body || document.documentElement);
                    swapIcons();
                    if (document.title !== APP) document.title = APP;
                }
                window.__aniDeskReapply = apply;
                apply();
                if (window.__aniDeskObserver) window.__aniDeskObserver.disconnect();
                var timer = null;
                window.__aniDeskObserver = new MutationObserver(function(){
                    if (timer) return;
                    timer = setTimeout(function(){
                        timer = null;
                        apply();
                    }, 30);
                });
                window.__aniDeskObserver.observe(document.documentElement, {
                    subtree: true,
                    childList: true,
                    characterData: true,
                    attributes: true,
                    attributeFilter: ['src', 'srcset', 'style']
                });
            })();
        """.trimIndent()
    }

    companion object {
        const val EXTRA_ACCESS_TOKEN = "vk_access_token"
        const val EXTRA_ERROR = "vk_error"

        private const val TAG = "VkOAuth"
        private const val CLIENT_ID = "54701760"
        private const val REDIRECT_SCHEME = "vk54701760"
        private const val REDIRECT_HOST = "vk.ru"
        private const val REDIRECT_PATH = "/blank.html"
        private const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST$REDIRECT_PATH"
        private const val AUTH_ENDPOINT = "https://id.vk.ru/authorize"
        private const val TOKEN_ENDPOINT = "https://id.vk.ru/oauth2/auth"
        private const val SDK_VERSION = "2.7.2"
        private const val KEY_VERIFIER = "vk_code_verifier"
        private const val KEY_STATE = "vk_state"

        private fun generateCodeVerifier(): String {
            val bytes = ByteArray(64)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(
                bytes,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
        }

        private fun codeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64.encodeToString(
                digest,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
        }
    }
}
