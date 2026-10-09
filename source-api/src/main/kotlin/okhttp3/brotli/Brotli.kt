package okhttp3.brotli

import okhttp3.Interceptor

object Brotli {
    @JvmStatic
    fun interceptor(): Interceptor = BrotliInterceptor

    @JvmStatic
    val interceptor: Interceptor
        get() = BrotliInterceptor
}
