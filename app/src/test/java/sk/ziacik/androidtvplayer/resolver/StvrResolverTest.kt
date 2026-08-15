package sk.ziacik.androidtvplayer.resolver

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel

class StvrResolverTest {
    @Test
    fun `loads landing page before live JSON and returns playable source`() = runTest {
        val http = RecordingHttpClient(
            responses = ArrayDeque(
                listOf(
                    "landing",
                    responseWith("https://cdn.example/first.m3u8?auth=one"),
                ),
            ),
        )

        val result = StvrResolver(http).resolve(TvChannel.JEDNOTKA)

        assertEquals(
            listOf(STVR_LANDING_URL, stvrLiveUrl(TvChannel.JEDNOTKA)),
            http.calls.map(Call::url),
        )
        assertEquals(STVR_USER_AGENT, http.calls[0].headers["User-Agent"])
        assertEquals(STVR_USER_AGENT, http.calls[1].headers["User-Agent"])
        assertTrue(result is StreamResolution.Playable)
        result as StreamResolution.Playable
        assertEquals("https://cdn.example/first.m3u8?auth=one", result.source.url)
        assertEquals(STVR_USER_AGENT, result.source.userAgent)
    }

    @Test
    fun `loads Dvojka live JSON and returns playable source`() = runTest {
        val http = RecordingHttpClient(
            responses = ArrayDeque(
                listOf(
                    "landing",
                    responseWith("https://cdn.example/dvojka.m3u8?auth=two"),
                ),
            ),
        )

        val result = StvrResolver(http).resolve(TvChannel.DVOJKA)

        assertEquals(STVR_LANDING_URL, http.calls[0].url)
        assertEquals(
            "https://www.rtvs.sk/json/live5f.json?c=2&ad=1&b=chrome&p=win&v=77&f=0&d=1",
            http.calls[1].url,
        )
        assertTrue(result is StreamResolution.Playable)
        result as StreamResolution.Playable
        assertEquals("https://cdn.example/dvojka.m3u8?auth=two", result.source.url)
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

        resolver.resolve(TvChannel.JEDNOTKA)
        val second = resolver.resolve(TvChannel.JEDNOTKA) as StreamResolution.Playable

        assertEquals(4, http.calls.size)
        assertEquals("https://cdn.example/second.m3u8?auth=two", second.source.url)
    }

    @Test
    fun `internet N returns unavailable without requiring HLS`() = runTest {
        val http = RecordingHttpClient(
            ArrayDeque(
                listOf(
                    "landing",
                    """{"clip":{
                      "series":"Ordinácia v Eifeli",
                      "subtitle":"Šance",
                      "internet":"N",
                      "timestop":"1786791292000",
                      "sources":[]
                    }}""",
                ),
            ),
        )

        assertEquals(
            StreamResolution.Unavailable(
                ProgramMetadata(
                    title = "Ordinácia v Eifeli: Šance",
                    startsAtMs = null,
                    endsAtMs = 1_786_791_292_000L,
                    internetAllowed = false,
                ),
            ),
            StvrResolver(http).resolve(TvChannel.JEDNOTKA),
        )
    }

    @Test
    fun `missing internet flag remains playable when HLS exists`() = runTest {
        val result = StvrResolver(
            RecordingHttpClient(
                ArrayDeque(
                    listOf(
                        "landing",
                        responseWith("https://cdn.example/live.m3u8"),
                    ),
                ),
            ),
        ).resolve(TvChannel.JEDNOTKA)

        assertTrue(result is StreamResolution.Playable)
    }

    @Test
    fun `playable response without HLS is rejected`() {
        val resolver = StvrResolver(
            RecordingHttpClient(
                ArrayDeque(
                    listOf(
                        "landing",
                        """{"clip":{"internet":"Y","sources":[]}}""",
                    ),
                ),
            ),
        )

        val error = assertThrows(StreamResolveException::class.java) {
            runTest { resolver.resolve(TvChannel.JEDNOTKA) }
        }

        assertEquals("STVR response does not contain an HLS source", error.message)
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
            runTest { resolver.resolve(TvChannel.JEDNOTKA) }
        }

        assertEquals("STVR request failed", error.message)
    }

    @Test
    fun `cancellation is not wrapped as a stream resolve failure`() {
        val cancellation = CancellationException("channel switched")
        val resolver = StvrResolver(
            object : StvrHttpClient {
                override suspend fun get(
                    url: String,
                    headers: Map<String, String>,
                ): String = throw cancellation
            },
        )

        val error = assertThrows(CancellationException::class.java) {
            runTest { resolver.resolve(TvChannel.JEDNOTKA) }
        }

        assertSame(cancellation, error)
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
