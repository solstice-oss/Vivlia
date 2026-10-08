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

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.source.R
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    override fun shouldIntercept(response: Response): Boolean {
        return response.header("cf-mitigated") == "challenge" &&
            response.header("Server") in SERVER_CHECK
    }

    override fun intercept(
        chain: Interceptor.Chain,
        request: Request,
        response: Response,
    ): Response {
        try {
            response.close()
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            val oldCookie = cookieManager.get(request.url)
                .firstOrNull { it.name == "cf_clearance" }
            resolveWithWebView(request, oldCookie)

            return chain.proceed(request)
        } catch (e: CloudflareBypassException) {
            throw IOException(context.getString(R.string.cloudflare_bypass_failure), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?) {
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        var challengeFound = false
        var cloudflareBypassed = false
        var webViewOutdated = false

        val originalUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)

        runOnMainThread {
            val challengeWebView = createWebView(originalRequest)
            webView = challengeWebView

            challengeWebView.addJavascriptInterface(
                object {
                    @Suppress("unused")
                    @JavascriptInterface
                    fun interactiveDetected() {
                        latch.countDown()
                    }
                },
                "mihon",
            )

            challengeWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val newClearanceCookie = cookieManager.get(originalRequest.url)
                        .firstOrNull { it.name == "cf_clearance" }
                        .let { it != null && it != oldCookie }
                    if (newClearanceCookie) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }

                    if (url == originalUrl) {
                        if (!challengeFound) {
                            latch.countDown()
                        } else {
                            view.evaluateJavascript(
                                """
                                    addEventListener("message", ({data}) => {
                                        if (data?.source === "cloudflare-challenge" && data?.event === "interactiveBegin") {
                                            mihon.interactiveDetected();
                                        }
                                    })
                                """.trimIndent(),
                                null,
                            )
                        }
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        if (errorResponse?.responseHeaders?.get("cf-mitigated") == "challenge") {
                            challengeFound = true
                        } else {
                            latch.countDown()
                        }
                    }
                }
            }

            challengeWebView.loadUrl(originalUrl, headers)
        }

        latch.awaitFor30Seconds()

        runOnMainThread {
            if (!cloudflareBypassed) {
                webViewOutdated = webView?.let(::isWebViewOutdated) == true
            }

            webView?.run {
                stopLoading()
                destroy()
            }
        }

        if (!cloudflareBypassed) {
            if (webViewOutdated) {
                showToast(R.string.cloudflare_webview_outdated, Toast.LENGTH_LONG)
            }

            throw CloudflareBypassException()
        }
    }
}

private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")

private class CloudflareBypassException : Exception()
