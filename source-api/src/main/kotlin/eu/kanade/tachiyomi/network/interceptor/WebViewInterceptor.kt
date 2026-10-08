/*
 * Copyright 2015 Tachiyomi Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License me.
 */

package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import eu.kanade.tachiyomi.source.R
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class WebViewInterceptor(
    private val context: Context,
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    private val executor = ContextCompat.getMainExecutor(context)

    private val initWebView by lazy {
        if (isMiui() || (Build.VERSION.SDK_INT == Build.VERSION_CODES.S &&
                Build.MANUFACTURER.equals("samsung", ignoreCase = true))
        ) {
            return@lazy
        }

        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (_: Exception) {
            // WebView initialization can fail while the system WebView is being updated.
        }
    }

    abstract fun shouldIntercept(response: Response): Boolean

    abstract fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!shouldIntercept(response)) {
            return response
        }

        if (!supportsWebView()) {
            executor.execute {
                Toast.makeText(context, R.string.cloudflare_webview_required, Toast.LENGTH_LONG).show()
            }
            return response
        }

        initWebView
        return intercept(chain, request, response)
    }

    fun parseHeaders(headers: Headers): Map<String, String> {
        return headers
            .filter { (name, value) -> isRequestHeaderSafe(name, value) }
            .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
            .mapValues { it.value.firstOrNull().orEmpty() }
    }

    fun CountDownLatch.awaitFor30Seconds() {
        await(30, TimeUnit.SECONDS)
    }

    fun createWebView(request: Request): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportMultipleWindows(true)
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            CookieManager.getInstance().acceptThirdPartyCookies(this)
            setUserAgent(request.header("User-Agent") ?: defaultUserAgentProvider())
        }
    }

    fun isWebViewOutdated(webView: WebView): Boolean {
        val originalUserAgent = webView.settings.userAgentString
        webView.settings.userAgentString = null
        val defaultUserAgent = webView.settings.userAgentString.orEmpty()
        webView.settings.userAgentString = originalUserAgent

        val majorVersion = WEBVIEW_VERSION_REGEX.matchEntire(defaultUserAgent)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull() ?: 0
        return majorVersion < MINIMUM_WEBVIEW_VERSION
    }

    fun showToast(messageRes: Int, duration: Int = Toast.LENGTH_LONG) {
        executor.execute {
            Toast.makeText(context, messageRes, duration).show()
        }
    }

    fun runOnMainThread(block: () -> Unit) {
        executor.execute(block)
    }

    private fun supportsWebView(): Boolean {
        return try {
            CookieManager.getInstance()
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
        } catch (e: RuntimeException) {
            Log.e(TAG, "WebView is unavailable", e)
            false
        }
    }

    private fun WebView.setUserAgent(userAgent: String) {
        settings.userAgentString = userAgent

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return

        val versionMatch = CHROME_VERSION_REGEX.find(userAgent) ?: return
        val majorVersion = versionMatch.groupValues[1]
        val fullVersion = majorVersion + versionMatch.groupValues[2].ifEmpty { ".0.0.0" }

        try {
            val metadata = WebSettingsCompat.getUserAgentMetadata(settings)
            val brandVersionList = metadata.brandVersionList.map { brandVersion ->
                val brand = when (brandVersion.brand) {
                    WEBVIEW_BRAND -> CHROME_BRAND
                    CHROMIUM_BRAND -> CHROMIUM_BRAND
                    else -> return@map brandVersion
                }

                UserAgentMetadata.BrandVersion.Builder()
                    .setBrand(brand)
                    .setMajorVersion(majorVersion)
                    .setFullVersion(fullVersion)
                    .build()
            }

            WebSettingsCompat.setUserAgentMetadata(
                settings,
                UserAgentMetadata.Builder(metadata)
                    .setBrandVersionList(brandVersionList)
                    .setFullVersion(fullVersion)
                    .build(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set WebView user-agent metadata", e)
        }
    }

    private fun isMiui(): Boolean {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getDeclaredMethod("get", String::class.java)
            (get.invoke(null, "ro.miui.ui.version.name") as? String).orEmpty().isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun isRequestHeaderSafe(name: String, value: String): Boolean {
        val normalizedName = name.lowercase(Locale.ENGLISH)
        val normalizedValue = value.lowercase(Locale.ENGLISH)
        if (normalizedName in unsafeHeaderNames || normalizedName.startsWith("proxy-")) return false
        return normalizedName != "connection" || normalizedValue != "upgrade"
    }

    private companion object {
        const val TAG = "WebViewInterceptor"
        const val MINIMUM_WEBVIEW_VERSION = 118
        const val WEBVIEW_BRAND = "Android WebView"
        const val CHROMIUM_BRAND = "Chromium"
        const val CHROME_BRAND = "Google Chrome"
        val CHROME_VERSION_REGEX = """Chrome/(\d+)(\.[\d.]+)?""".toRegex()
        val WEBVIEW_VERSION_REGEX = """.*Chrome/(\d+)\..*""".toRegex()
        val unsafeHeaderNames = listOf(
            "content-length",
            "host",
            "trailer",
            "te",
            "upgrade",
            "cookie2",
            "keep-alive",
            "transfer-encoding",
            "set-cookie",
        )
    }
}
