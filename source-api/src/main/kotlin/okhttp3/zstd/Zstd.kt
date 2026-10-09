package okhttp3.zstd

import okhttp3.Interceptor
import okhttp3.Response

object Zstd {
    @JvmStatic
    fun interceptor(): Interceptor = ZstdInterceptor()

    @JvmStatic
    val interceptor: Interceptor
        get() = ZstdInterceptor()
}

class ZstdInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(chain.request())
    }
}
