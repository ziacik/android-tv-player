package sk.ziacik.androidtvplayer.resolver

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OkHttpStvrClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(InMemoryCookieJar())
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
) : StvrHttpClient {
    override suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): String {
        val request = Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (name, value) -> header(name, value) }
            }
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            val completed = AtomicBoolean(false)
            continuation.invokeOnCancellation {
                completed.compareAndSet(false, true)
                call.cancel()
            }
            val callback = object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (completed.compareAndSet(false, true)) {
                        continuation.resumeWith(Result.failure(e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.use {
                            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                            it.body.string()
                        }
                        if (completed.compareAndSet(false, true)) {
                            continuation.resumeWith(Result.success(body))
                        }
                    } catch (error: Exception) {
                        if (completed.compareAndSet(false, true)) {
                            continuation.resumeWith(Result.failure(error))
                        }
                    }
                }
            }

            try {
                call.enqueue(callback)
            } catch (error: Exception) {
                if (completed.compareAndSet(false, true)) {
                    continuation.resumeWith(Result.failure(error))
                }
            }
        }
    }
}

private class InMemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { incoming ->
            this.cookies.removeAll { existing ->
                existing.name == incoming.name &&
                    existing.domain == incoming.domain &&
                    existing.path == incoming.path
            }
            if (incoming.expiresAt > System.currentTimeMillis()) {
                this.cookies += incoming
            }
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { cookie -> cookie.expiresAt <= now }
        return cookies.filter { cookie -> cookie.matches(url) }
    }
}
