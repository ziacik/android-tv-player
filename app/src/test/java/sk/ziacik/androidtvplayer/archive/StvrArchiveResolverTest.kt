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
	fun `resolves archive stream from JSON archive API item`() = runTest {
		val apiUrl = archiveApiUrl("2026-09-08")
		val client = ResolverStvrHttpClient(
			mapOf(
				apiUrl to archiveListing(
					archiveItem(id = 617992, name = "Ranné správy", air = "2026-09-08 07:00:00"),
				),
				"https://www.rtvs.sk/json/archive5f.json?id=617992" to
					"""{"clip":{"sources":[{"src":"https://cdn.example/archive.m3u8","type":"application/x-mpegurl"}]}}""",
			),
		)

		val result = StvrArchiveResolver(client).resolve(
			channel = directJednotka(),
			startsAtMs = 1_788_843_600_000L,
			title = "Ranné správy",
		)

		assertEquals("https://cdn.example/archive.m3u8", result.url)
		assertEquals(
			listOf(
				apiUrl,
				"https://www.rtvs.sk/json/archive5f.json?id=617992",
			),
			client.requestedUrls,
		)
	}

	@Test
	fun `matches nearest JSON archive item when STVR start is shifted from EPG`() = runTest {
		val client = ResolverStvrHttpClient(
			mapOf(
				archiveApiUrl("2026-09-08") to archiveListing(
					archiveItem(id = 618007, name = "Duel", air = "2026-09-08 17:44:00"),
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

	@Test
	fun `resolves repeat from original airing JSON archive date`() = runTest {
		val client = ResolverStvrHttpClient(
			mapOf(
				archiveApiUrl("2026-09-09") to archiveListing(),
				archiveApiUrl("2026-09-08") to archiveListing(
					archiveItem(id = 618007, name = "Duel", air = "2026-09-08 17:44:00"),
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
	fun `reports JSON archive diagnostics when item is missing`() = runTest {
		val client = ResolverStvrHttpClient(
			mapOf(
				archiveApiUrl("2026-09-21") to archiveListing(
					archiveItem(id = 620530, name = "Duel", air = "2026-09-21 17:45:00"),
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
			val message = error.message.orEmpty()
			assertTrue(message.contains("expectedTime=10:45"))
			assertTrue(message.contains("expectedTitle=duel"))
			assertTrue(message.contains("archiveItems=1"))
			assertTrue(message.contains("archiveIds=620530"))
		}
	}

	private fun directJednotka() = TvChannel(
		storageKey = "jednotka",
		displayName = "JEDNOTKA",
		provider = ChannelProvider.DIRECT,
		providerValue = "https://example.com/live.m3u8",
		archive = ArchiveConfig(ArchiveProvider.STVR, channelId = "1"),
	)
}

private class ResolverStvrHttpClient(
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
