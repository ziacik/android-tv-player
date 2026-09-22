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
    fun `resolves playable HLS from archive API item`() = runTest {
        val client = FakeStvrHttpClient(
            responses = mapOf(
                archiveApiUrl("2026-09-21") to archiveApi(
                    archiveItem(620530, "Duel", "2026-09-21 17:45:00"),
                ),
                "https://www.rtvs.sk/json/archive5f.json?id=620530" to
                    """{"clip":{"sources":[{"src":"https://cdn.example/duel.m3u8","type":"application/x-mpegurl"}]}}""",
            ),
        )

        val result = StvrArchiveResolver(client).resolve(
            channel = directJednotka(),
            startsAtMs = 1_790_005_500_000L,
            title = "Duel",
        )

        assertEquals("https://cdn.example/duel.m3u8", result.url)
        assertEquals(
            listOf(
                archiveApiUrl("2026-09-21"),
                "https://www.rtvs.sk/json/archive5f.json?id=620530",
            ),
            client.requestedUrls,
        )
    }

    @Test
    fun `does not confuse another broadcast of same title with archived Duel`() = runTest {
        val client = FakeStvrHttpClient(
            responses = mapOf(
                archiveApiUrl("2026-09-21") to archiveApi(
                    archiveItem(620530, "Duel", "2026-09-21 17:45:00"),
                ),
            ),
        )

        try {
            StvrArchiveResolver(client).resolve(
                channel = directJednotka(),
                startsAtMs = 1_789_980_300_000L,
                title = "Duel",
            )
            fail("Expected archive lookup to fail")
        } catch (error: StreamResolveException) {
            assertTrue(error.message.orEmpty().contains("expectedTitle=duel"))
            assertTrue(error.message.orEmpty().contains("archiveItems=1"))
        }
    }

    @Test
    fun `resolves repeat from original airing archive date`() = runTest {
        val client = FakeStvrHttpClient(
            responses = mapOf(
                archiveApiUrl("2026-09-09") to archiveApi(),
                archiveApiUrl("2026-09-08") to archiveApi(
                    archiveItem(618007, "Duel", "2026-09-08 17:44:00"),
                ),
                "https://www.rtvs.sk/json/archive5f.json?id=618007" to
                    """{"clip":{"sources":[{"src":"https://cdn.example/duel-repeat.m3u8","type":"application/x-mpegurl"}]}}""",
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
                archiveApiUrl("2026-09-09"),
                archiveApiUrl("2026-09-08"),
                "https://www.rtvs.sk/json/archive5f.json?id=618007",
            ),
            client.requestedUrls,
        )
    }

    @Test
    fun `matches archive item when STVR start differs slightly from EPG`() = runTest {
        val client = FakeStvrHttpClient(
            responses = mapOf(
                archiveApiUrl("2026-09-08") to archiveApi(
                    archiveItem(618007, "Duel", "2026-09-08 17:44:00"),
                ),
                "https://www.rtvs.sk/json/archive5f.json?id=618007" to
                    """{"clip":{"sources":[{"src":"https://cdn.example/duel.m3u8","type":"application/x-mpegurl"}]}}""",
            ),
        )

        val result = StvrArchiveResolver(client).resolve(
            channel = directJednotka(),
            startsAtMs = 1_788_882_000_000L,
            title = "Duel",
        )

        assertEquals("https://cdn.example/duel.m3u8", result.url)
    }

    private fun directJednotka() = TvChannel(
        storageKey = "jednotka",
        displayName = "JEDNOTKA",
        provider = ChannelProvider.DIRECT,
        providerValue = "https://example.com/live.m3u8",
        archive = ArchiveConfig(ArchiveProvider.STVR, channelId = "1"),
    )
}

private fun archiveApiUrl(date: String): String =
    "https://www.stvr.sk/json/tv/archiv?e=1&archive=1&d=${date}&p=1&l=100&o=desc"

private fun archiveApi(vararg items: String): String = """
    {
        "paging": {
            "page": "1",
            "size": "100",
            "results": "${items.size}"
        },
        "program": [
            ${items.joinToString(",\n")}
        ]
    }
""".trimIndent()

private fun archiveItem(
    id: Int,
    name: String,
    air: String,
): String = """
    {
        "ID": ${id},
        "series": 14126,
        "name": "${name}",
        "air": "${air}",
        "license": ""
    }
""".trimIndent()

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
