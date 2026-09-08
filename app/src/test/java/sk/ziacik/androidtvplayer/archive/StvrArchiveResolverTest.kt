package sk.ziacik.androidtvplayer.archive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import sk.ziacik.androidtvplayer.channel.TvChannel

class StvrArchiveResolverTest {
    @Test
    fun `resolves archive id from STVR date listing and requests archive stream`() = runTest {
        val client = FakeStvrArchiveHttpClient(
            responses = mapOf(
                "https://www.stvr.sk/televizia/archiv?date=2026-09-08&ord=dt" to """
                    <h2>Jednotka</h2>
                    <div>07:00 - 08:29</div>
                    <a href="/televizia/archiv/14026/617992">Ranné správy</a>
                """.trimIndent(),
                "https://www.rtvs.sk/json/archive5f.json?id=617992" to """
                    {"clip":{"sources":[{"src":"https://cdn.example/archive.m3u8"}]}}
                """.trimIndent(),
            ),
        )
        val resolver = StvrArchiveResolver(client)

        val result = resolver.resolve(
            channel = TvChannel.JEDNOTKA,
            startsAtMs = 1788843600000L,
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

private class FakeStvrArchiveHttpClient(
    private val responses: Map<String, String>,
) : StvrArchiveHttpClient {
    val requestedUrls = mutableListOf<String>()

    override suspend fun get(url: String): String {
        requestedUrls += url
        return responses.getValue(url)
    }
}
