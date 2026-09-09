package sk.ziacik.androidtvplayer.archive

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.ArchiveConfig
import sk.ziacik.androidtvplayer.channel.ArchiveProvider
import sk.ziacik.androidtvplayer.channel.ChannelProvider
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.StvrHttpClient
import sk.ziacik.androidtvplayer.resolver.StreamResolveException

class StvrArchiveResolverTest {
    @Test
    fun `resolves archive id from metadata on a direct live channel`() = runTest {
        val client = FakeStvrHttpClient(
            responses = mapOf(
                "https://www.stvr.sk/televizia/archiv?date=2026-09-08&ord=dt" to """
                    <div class="media">
                        <div class="media__body">
                            <div class="program time--start">07:00 <span>- 08:29</span></div>
                            <a href="/televizia/archiv/14026/617992"><img src="image.jpg"></a>
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
        val directJednotka = directJednotka()

        val result = resolver.resolve(
            channel = directJednotka,
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

    @Test
    fun `matches archive programme when actual STVR start is shifted from EPG`() = runTest {
        val client = FakeStvrHttpClient(
            responses = mapOf(
                "https://www.stvr.sk/televizia/archiv?date=2026-09-08&ord=dt" to """
                    <h2>Jednotka</h2>
                    <div class="media media--archive">
                        <div class="media__body">
                            <div data-role="start" class="time--start program">17:44 <span>- 18:12</span></div>
                            <a class="media__image" href="/televizia/archiv/14126/618007"><img src="duel.jpg"></a>
                            <h5><a title="Duel" href="/televizia/archiv/14126/618007" class="link program__title">Duel</a></h5>
                        </div>
                    </div>
                    <h2>Dvojka</h2>
                """.trimIndent(),
                "https://www.rtvs.sk/json/archive5f.json?id=618007" to """
                    {"clip":{"sources":[{"src":"https://cdn.example/duel.m3u8","type":"application/x-mpegurl"}]}}
                """.trimIndent(),
            ),
        )

        val result = StvrArchiveResolver(client).resolve(
            channel = directJednotka(),
            startsAtMs = 1_788_882_000_000L,
            title = "Duel",
        )

        assertEquals("https://cdn.example/duel.m3u8", result.url)
    }

    @Test
    fun `resolves a repeat from the original airing archive date`() = runTest {
        val client = FakeStvrHttpClient(
            responses = mapOf(
                "https://www.stvr.sk/televizia/archiv?date=2026-09-09&ord=dt" to """
                    <h2>Jednotka</h2>
                    <div class="media">
                        <div class="program time--start">07:00 <span>- 08:29</span></div>
                        <a href="/televizia/archiv/14126/618025"><img src="morning.jpg"></a>
                        <h5><a class="link" title="Ranné správy">Ranné správy</a></h5>
                    </div>
                    <h2>Dvojka</h2>
                """.trimIndent(),
                "https://www.stvr.sk/televizia/archiv?date=2026-09-08&ord=dt" to """
                    <h2>Jednotka</h2>
                    <div class="media media--archive">
                        <div class="media__body">
                            <div class="program time--start">17:44 <span>- 18:12</span></div>
                            <a href="/televizia/archiv/14126/618007"><img src="duel.jpg"></a>
                            <h5><a class="link" title="Duel">Duel</a></h5>
                        </div>
                    </div>
                    <h2>Dvojka</h2>
                """.trimIndent(),
                "https://www.rtvs.sk/json/archive5f.json?id=618007" to """
                    {"clip":{"sources":[{"src":"https://cdn.example/duel-repeat.m3u8","type":"application/x-mpegurl"}]}}
                """.trimIndent(),
            ),
        )

        val result = StvrArchiveResolver(client).resolve(
            channel = directJednotka(),
            startsAtMs = 1_788_943_500_000L,
            title = "Duel",
            originalStartsAtMs = 1_788_882_300_000L,
        )

        assertEquals("https://cdn.example/duel-repeat.m3u8", result.url)
        assertEquals(
            listOf(
                "https://www.stvr.sk/televizia/archiv?date=2026-09-09&ord=dt",
                "https://www.stvr.sk/televizia/archiv?date=2026-09-08&ord=dt",
                "https://www.rtvs.sk/json/archive5f.json?id=618007",
            ),
            client.requestedUrls,
        )
    }

    @Test
    fun `reports useful diagnostics when archive HTML cannot be parsed`() = runTest {
        val client = FakeStvrHttpClient(
            responses = mapOf(
                "https://www.stvr.sk/televizia/archiv?date=2026-09-08&ord=dt" to """
                    <h2>Jednotka</h2>
                    <div class="media media--archive">
                        <a class="media__image" href="/televizia/archiv/14126/618007">Duel</a>
                    </div>
                    <h2>Dvojka</h2>
                """.trimIndent(),
            ),
        )

        try {
            StvrArchiveResolver(client).resolve(
                channel = directJednotka(),
                startsAtMs = 1_788_882_000_000L,
                title = "Duel",
            )
            fail("Expected archive lookup to fail")
        } catch (error: StreamResolveException) {
            val message = error.message.orEmpty()
            assertTrue(message.contains("expectedTime=17:40"))
            assertTrue(message.contains("expectedTitle=duel"))
            assertTrue(message.contains("archiveLinks=1"))
            assertTrue(message.contains("parsedCandidates=0"))
            assertTrue(message.contains("archiveIds=618007"))
        }
    }

    @Test
    fun `keeps searching inside channel when programme titles use headings`() = runTest {
        val client = FakeStvrHttpClient(
            responses = mapOf(
                "https://www.stvr.sk/televizia/archiv?date=2026-09-08&ord=dt" to """
                    <h2>Jednotka</h2>
                    <div class="media">
                        <div class="program time--start">06:00 <span>- 06:29</span></div>
                        <a href="/televizia/archiv/14026/111111"><img src="early.jpg"></a>
                        <h5><a class="link" title="Skoré správy">Skoré správy</a></h5>
                    </div>
                    <div class="media">
                        <div class="program time--start">07:00 <span>- 08:29</span></div>
                        <a href="/televizia/archiv/14026/617992"><img src="morning.jpg"></a>
                        <h5><a class="link" title="Ranné správy">Ranné správy</a></h5>
                    </div>
                    <h2>Dvojka</h2>
                    <div class="media">
                        <div class="program time--start">07:00 <span>- 08:29</span></div>
                        <a href="/televizia/archiv/14026/999999"><img src="wrong.jpg"></a>
                        <h5><a class="link" title="Ranné správy">Ranné správy</a></h5>
                    </div>
                """.trimIndent(),
                "https://www.rtvs.sk/json/archive5f.json?id=617992" to """
                    {"clip":{"sources":[{"src":"https://cdn.example/archive.m3u8","type":"application/x-mpegurl"}]}}
                """.trimIndent(),
            ),
        )

        val result = StvrArchiveResolver(client).resolve(
            channel = directJednotka(),
            startsAtMs = 1_788_843_600_000L,
            title = "Ranné správy",
        )

        assertEquals("https://cdn.example/archive.m3u8", result.url)
        assertEquals(
            "https://www.rtvs.sk/json/archive5f.json?id=617992",
            client.requestedUrls.last(),
        )
    }

    private fun directJednotka() = TvChannel(
        storageKey = "jednotka",
        displayName = "JEDNOTKA",
        provider = ChannelProvider.DIRECT,
        providerValue = "https://example.com/live.m3u8",
        archive = ArchiveConfig(ArchiveProvider.STVR, channelId = "1"),
    )
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
