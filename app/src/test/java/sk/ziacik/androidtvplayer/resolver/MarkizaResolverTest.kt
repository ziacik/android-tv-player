package sk.ziacik.androidtvplayer.resolver

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel

class MarkizaResolverTest {
    @Test
    fun `logs in resolves embedded HLS source and supplies playback headers`() = runTest {
        val http = RecordingHttpClient(
            ArrayDeque(
                listOf(
                    MarkizaHttpResponse(
                        code = 200,
                        body = """<input type="hidden" name="_do" value="sign-loginForm-submit">""",
                    ),
                    MarkizaHttpResponse(code = 302, body = ""),
                    MarkizaHttpResponse(
                        code = 200,
                        body = """<iframe data-src="https://media.cms.markiza.sk/embed/markiza-live" allowfullscreen></iframe>""",
                    ),
                    MarkizaHttpResponse(
                        code = 200,
                        body = """"source":{"sources":[{"src":"https://cdn.example/markiza.m3u8?token=abc","type":"application/x-mpegurl"}]}""",
                    ),
                ),
            ),
        )

        val result = MarkizaResolver(http) {
            MarkizaCredentials("user@example.com", "secret")
        }.resolve(TvChannel.MARKIZA)

        assertTrue(result is StreamResolution.Playable)
        result as StreamResolution.Playable
        assertEquals("MARKÍZA", result.program.title)
        assertEquals("https://cdn.example/markiza.m3u8?token=abc", result.source.url)
        assertEquals(MARKIZA_USER_AGENT, result.source.userAgent)
        assertEquals(MARKIZA_ORIGIN, result.source.headers["Referer"])
        assertEquals(MARKIZA_ORIGIN, result.source.headers["Origin"])
        assertEquals(MARKIZA_USER_AGENT, result.source.headers["User-Agent"])
        assertEquals(
            listOf(
                HttpCall.Get(MARKIZA_LOGIN_URL, mapOf("User-Agent" to MARKIZA_USER_AGENT)),
                HttpCall.Post(
                    MARKIZA_LOGIN_URL,
                    mapOf("email" to "user@example.com", "password" to "secret", "_do" to "sign-loginForm-submit"),
                    mapOf("User-Agent" to MARKIZA_USER_AGENT, "Referer" to MARKIZA_LOGIN_URL),
                ),
                HttpCall.Get(
                    MARKIZA_LIVE_URL,
                    mapOf("User-Agent" to MARKIZA_USER_AGENT, "Referer" to MARKIZA_LOGIN_URL),
                ),
                HttpCall.Get(
                    "https://media.cms.markiza.sk/embed/markiza-live",
                    mapOf("User-Agent" to MARKIZA_USER_AGENT, "Referer" to MARKIZA_LIVE_URL),
                ),
            ),
            http.calls,
        )
    }

    @Test
    fun `reuses the authenticated session when Markiza is selected again`() = runTest {
        val http = RecordingHttpClient(
            ArrayDeque(
                listOf(
                    MarkizaHttpResponse(
                        code = 200,
                        body = """<input type="hidden" name="_do" value="sign-loginForm-submit">""",
                    ),
                    MarkizaHttpResponse(code = 302, body = ""),
                    MarkizaHttpResponse(
                        code = 200,
                        body = """<iframe data-src="https://media.cms.markiza.sk/embed/markiza-live"></iframe>""",
                    ),
                    MarkizaHttpResponse(
                        code = 200,
                        body = """"source":{"sources":[{"src":"https://cdn.example/first.m3u8","type":"application/x-mpegurl"}]}""",
                    ),
                    MarkizaHttpResponse(
                        code = 200,
                        body = """<iframe data-src="https://media.cms.markiza.sk/embed/markiza-live"></iframe>""",
                    ),
                    MarkizaHttpResponse(
                        code = 200,
                        body = """"source":{"sources":[{"src":"https://cdn.example/second.m3u8","type":"application/x-mpegurl"}]}""",
                    ),
                ),
            ),
        )
        val resolver = MarkizaResolver(http) {
            MarkizaCredentials("user@example.com", "secret")
        }

        resolver.resolve(TvChannel.MARKIZA)
        val result = resolver.resolve(TvChannel.MARKIZA)

        assertTrue(result is StreamResolution.Playable)
        assertEquals(6, http.calls.size)
        assertEquals(
            HttpCall.Get(
                MARKIZA_LIVE_URL,
                mapOf(
                    "User-Agent" to MARKIZA_USER_AGENT,
                    "Referer" to MARKIZA_LOGIN_URL,
                ),
            ),
            http.calls[4],
        )
    }

    @Test
    fun `requires credentials without making a network request`() = runTest {
        val http = RecordingHttpClient(ArrayDeque())

        val result = MarkizaResolver(http) { null }.resolve(TvChannel.MARKIZA)

        assertEquals(StreamResolution.RequiresCredentials(TvChannel.MARKIZA), result)
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun `shows credential form after rejected login`() = runTest {
        val http = RecordingHttpClient(
            ArrayDeque(
                listOf(
                    MarkizaHttpResponse(
                        code = 200,
                        body = """<input type="hidden" name="_do" value="sign-loginForm-submit">""",
                    ),
                    MarkizaHttpResponse(code = 401, body = ""),
                ),
            ),
        )

        val result = MarkizaResolver(http) {
            MarkizaCredentials("user@example.com", "wrong-password")
        }.resolve(TvChannel.MARKIZA)

        assertEquals(StreamResolution.RequiresCredentials(TvChannel.MARKIZA), result)
        assertEquals(2, http.calls.size)
    }

    private class RecordingHttpClient(
        private val responses: ArrayDeque<MarkizaHttpResponse>,
    ) : MarkizaHttpClient {
        val calls = mutableListOf<HttpCall>()

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
        ): MarkizaHttpResponse {
            calls += HttpCall.Get(url, headers)
            return responses.removeFirst()
        }

        override suspend fun postForm(
            url: String,
            form: Map<String, String>,
            headers: Map<String, String>,
        ): MarkizaHttpResponse {
            calls += HttpCall.Post(url, form, headers)
            return responses.removeFirst()
        }
    }

    private sealed interface HttpCall {
        data class Get(val url: String, val headers: Map<String, String>) : HttpCall

        data class Post(
            val url: String,
            val form: Map<String, String>,
            val headers: Map<String, String>,
        ) : HttpCall
    }
}
