package sk.ziacik.androidtvplayer.archive

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.StvrHttpClient

class StvrArchiveResolverTest {
    @Test
    fun `resolves archive id from STVR date listing and requests archive stream`() = runTest {
        val client = FakeStvrHttpClient(
            responses = mapOf(
                "https://www.stvr.sk/televizia/archiv?date=2026-09-08&ord=dt" to """
                    <div class="media">
                        <a href="/televizia/archiv/14026/617992"><img src="image.jpg"></a>
                        <div class="media__body">
                            <div class="program time--start">07:00 <span>- 08:29</span></div>
                            <a class="link" title="Ranné správy">Ranné správy</a>
                        </div>
                    </div>
                """.trimIndent(),
                "https://www.rtvs.sk/json/archive5f.json?id=617992" to """
                    {"clip":{"sources":[{"src":"https://cdn.example/archive.m3u8","type":"application/x-mpegurl"}]}}
                """.trimIndent(),
            ),
        )
        val resolver = StvrArchiveResolver(client)

        val result = resolver.resolve(
            channel = TvChannel.JEDNOTKA,
            startsAtMs = 1_788_843_600_000L,
            title = "Ranné správy",
        )

        assertEquals("https://cdn.example/archive.m3u8", result.url)
        assertEquals(
            listOf(
                "https://www.stvr.sk/televizia/archiv?date=2026-09-08&ord=dt",
                "https://www.rtvs.sk/json/archive5f.json?id=617992",
            ),
            client.requestedUrls,
        )
    }
}

private class FakeStvrHttpClient(
    private val responses: Map<String, String>,
) : StvrHttpClient {
    val requestedUrls = mutableListOf<String>()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): String {
        requestedUrls += url
        return responses.getValue(url)
    }
}
