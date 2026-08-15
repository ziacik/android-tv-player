package sk.ziacik.androidtvplayer.resolver

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

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
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (name, value) -> header(name, value) }
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            response.body.string()
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

