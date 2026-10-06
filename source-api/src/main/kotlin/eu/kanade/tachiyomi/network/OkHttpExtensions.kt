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

package eu.kanade.tachiyomi.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Producer
import rx.Subscription
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

val jsonMime = "application/json; charset=utf-8".toMediaType()

@Deprecated("Use suspend APIs instead")
fun Call.asObservable(): Observable<Response> {
    return Observable.unsafeCreate { subscriber ->
        val call = clone()

        val requestArbiter = object : Producer, Subscription {
            val boolean = AtomicBoolean(false)
            override fun request(n: Long) {
                if (n == 0L || !boolean.compareAndSet(false, true)) return

                try {
                    val response = call.execute()
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onNext(response)
                        subscriber.onCompleted()
                    }
                } catch (e: Exception) {
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onError(e)
                    }
                }
            }

            override fun unsubscribe() {
                call.cancel()
            }

            override fun isUnsubscribed(): Boolean {
                return call.isCanceled()
            }
        }

        subscriber.add(requestArbiter)
        subscriber.setProducer(requestArbiter)
    }
}

@Deprecated("Use suspend APIs instead")
fun Call.asObservableSuccess(): Observable<Response> {
    @Suppress("DEPRECATION")
    return asObservable().doOnNext { response ->
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
    }
}

private suspend fun Call.await(callStack: Array<StackTraceElement>): Response {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            try {
                this.cancel()
            } catch (_: Throwable) {
                // ignore
            }
        }

        this.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                val exception = IOException(e.message, e).apply { stackTrace = callStack }
                continuation.resumeWithException(exception)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ ->
                    value.close()
                }
            }
        })
    }
}

suspend fun Call.await(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    return await(callStack)
}

/**
 * Similar to [await] but throws [HttpException] if [Response.isSuccessful] returns false
 */
suspend fun Call.awaitSuccess(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    val response = await(callStack)
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code).apply { stackTrace = callStack }
    }
    return response
}

fun OkHttpClient.newCachelessCallWithProgress(
    request: Request,
    listener: ProgressListener,
    existingSize: Long = 0L,
): Call {
    val progressClient = newBuilder()
        .cache(null)
        .addNetworkInterceptor { chain ->
            val req = chain.request()
                .newBuilder()
                .apply {
                    if (existingSize > 0 && request.header("Range") == null) {
                        header("Range", "bytes=$existingSize-")
                    }
                }
                .build()

            val originalResponse = chain.proceed(req)
            val actualExistingSize = if (originalResponse.code == 206) existingSize else 0L
            originalResponse.newBuilder()
                .body(ProgressResponseBody(originalResponse.body, listener, actualExistingSize))
                .build()
        }
        .build()

    return progressClient.newCall(request)
}

inline fun <reified T> Response.parseAs(json: Json): T {
    return decodeFromJsonResponse(json, serializer(), this)
}

fun <T> decodeFromJsonResponse(
    json: Json,
    deserializer: DeserializationStrategy<T>,
    response: Response,
): T {
    return response.body.source().use {
        json.decodeFromBufferedSource(deserializer, it)
    }
}

@JvmName("decodeFromJsonResponseExtension")
fun <T> Json.decodeFromJsonResponse(
    deserializer: DeserializationStrategy<T>,
    response: Response,
): T {
    return response.body.source().use {
        decodeFromBufferedSource(deserializer, it)
    }
}
