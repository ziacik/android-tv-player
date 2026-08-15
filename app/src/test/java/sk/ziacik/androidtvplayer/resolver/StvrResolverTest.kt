package sk.ziacik.androidtvplayer.resolver

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StvrResolverTest {
    @Test
    fun `loads landing page before live JSON and returns HLS source`() = runTest {
        val http = RecordingHttpClient(
            responses = ArrayDeque(
                listOf(
                    "landing",
                    responseWith("https://cdn.example/first.m3u8?auth=one"),
                ),
            ),
        )

        val source = StvrResolver(http).resolve()

        assertEquals(
            listOf(STVR_LANDING_URL, STVR_LIVE_URL),
            http.calls.map(Call::url),
        )
        assertEquals(STVR_USER_AGENT, http.calls[0].headers["User-Agent"])
        assertEquals(STVR_USER_AGENT, http.calls[1].headers["User-Agent"])
        assertEquals("https://cdn.example/first.m3u8?auth=one", source.url)
        assertEquals(STVR_USER_AGENT, source.userAgent)
    }

    @Test
    fun `each resolve requests a fresh token`() = runTest {
        val http = RecordingHttpClient(
            responses = ArrayDeque(
                listOf(
                    "landing",
                    responseWith("https://cdn.example/first.m3u8?auth=one"),
                    "landing",
                    responseWith("https://cdn.example/second.m3u8?auth=two"),
                ),
            ),
        )
        val resolver = StvrResolver(http)

        resolver.resolve()
        val second = resolver.resolve()

        assertEquals(4, http.calls.size)
        assertEquals("https://cdn.example/second.m3u8?auth=two", second.url)
    }

    @Test
    fun `wraps network failures without exposing a token`() {
        val resolver = StvrResolver(
            object : StvrHttpClient {
                override suspend fun get(
                    url: String,
                    headers: Map<String, String>,
                ): String = throw IOException("offline")
            },
        )

        val error = assertThrows(StreamResolveException::class.java) {
            runTest { resolver.resolve() }
        }

        assertEquals("STVR request failed", error.message)
    }

    private fun responseWith(url: String): String = """
        {"clip":{"sources":[
          {"src":"$url","type":"application/x-mpegurl"}
        ]}}
    """.trimIndent()

    private data class Call(
        val url: String,
        val headers: Map<String, String>,
    )

    private class RecordingHttpClient(
        private val responses: ArrayDeque<String>,
    ) : StvrHttpClient {
        val calls = mutableListOf<Call>()

        override suspend fun get(
            url: String,
            headers: Map<String, String>,
        ): String {
            calls += Call(url, headers)
            return responses.removeFirst()
        }
    }
}
