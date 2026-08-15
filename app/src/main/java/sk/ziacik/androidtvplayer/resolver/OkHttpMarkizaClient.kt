package sk.ziacik.androidtvplayer.resolver

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OkHttpMarkizaClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(MarkizaCookieJar())
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) : MarkizaHttpClient {
    override suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): MarkizaHttpResponse = execute(
        Request.Builder().url(url).applyHeaders(headers).build(),
    )

    override suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
    ): MarkizaHttpResponse {
        val body = FormBody.Builder().apply {
            form.forEach { (name, value) -> add(name, value) }
        }.build()
        return execute(Request.Builder().url(url).post(body).applyHeaders(headers).build())
    }

    private suspend fun execute(request: Request): MarkizaHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            val completed = AtomicBoolean(false)
            continuation.invokeOnCancellation {
                completed.compareAndSet(false, true)
                call.cancel()
            }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (completed.compareAndSet(false, true)) {
                            continuation.resumeWith(Result.failure(e))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val result = response.use {
                                MarkizaHttpResponse(it.code, it.body.string())
                            }
                            if (completed.compareAndSet(false, true)) {
                                continuation.resumeWith(Result.success(result))
                            }
                        } catch (error: Exception) {
                            if (completed.compareAndSet(false, true)) {
                                continuation.resumeWith(Result.failure(error))
                            }
                        }
                    }
                },
            )
        }

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder = apply {
        headers.forEach { (name, value) -> header(name, value) }
    }
}

private class MarkizaCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { incoming ->
            this.cookies.removeAll { existing ->
                existing.name == incoming.name &&
                    existing.domain == incoming.domain &&
                    existing.path == incoming.path
            }
            if (incoming.expiresAt > System.currentTimeMillis()) this.cookies += incoming
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return cookies.filter { it.matches(url) }
    }
}
