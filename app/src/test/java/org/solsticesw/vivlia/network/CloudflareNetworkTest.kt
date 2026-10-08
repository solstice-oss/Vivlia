package org.solsticesw.vivlia.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

@RunWith(RobolectricTestRunner::class)
class CloudflareNetworkTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val networkHelper by lazy {
        NetworkHelper(context).also { Injekt.addSingleton(it) }
    }

    @Test
    fun `extension default client exposes required Cloudflare interceptor`() {
        val client = networkHelper.client

        assertEquals(1, client.interceptors.count { it is CloudflareInterceptor })
        assertTrue(client.interceptors.any { it.javaClass.simpleName == "CloudflareInterceptor" })
        assertTrue(client.interceptors.any { it is CloudflareInterceptor })
        assertTrue(client.interceptors.any { it.javaClass.simpleName == "UncaughtExceptionInterceptor" })
        assertTrue(client.interceptors.any { it.javaClass.simpleName == "UserAgentInterceptor" })
        assertTrue(client.cookieJar is AndroidCookieJar)
        assertNotNull(client.cache)
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(120_000, client.callTimeoutMillis)

        val source = object : HttpSource() {
            override val baseUrl = "https://source.invalid"
            override val name = "test"
            override val lang = "en"
            override val supportsLatest = false
        }
        assertTrue(source.client.interceptors.any { it.javaClass.simpleName == "CloudflareInterceptor" })
    }

    @Test
    fun `cloning extension default client preserves exactly one Cloudflare interceptor`() {
        val client = networkHelper.client

        repeat(3) {
            val rebuiltClient = client.newBuilder().build()
            assertEquals(1, rebuiltClient.interceptors.count { it is CloudflareInterceptor })
        }
    }

    @Test
    fun `independent native OkHttp clients are not globally modified`() {
        val nativeClient = OkHttpClient()
        networkHelper

        assertFalse(nativeClient.interceptors.any { it is CloudflareInterceptor })
    }
}
