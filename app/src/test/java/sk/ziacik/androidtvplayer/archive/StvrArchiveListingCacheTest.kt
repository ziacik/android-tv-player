package sk.ziacik.androidtvplayer.archive

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.ArchiveConfig
import sk.ziacik.androidtvplayer.channel.ArchiveProvider
import sk.ziacik.androidtvplayer.channel.ChannelProvider
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.StvrHttpClient

class StvrArchiveListingCacheTest {
	@Test
	fun `reuses one STVR JSON day listing for multiple archive programmes`() = runTest {
		val listingUrl = cacheArchiveApiUrl("2026-09-08")
		val client = RecordingCacheStvrHttpClient(
			responses = mapOf(
				listingUrl to cacheArchiveListing(
					cacheArchiveItem(id = 617992, name = "Ranné správy", air = "2026-09-08 07:00:00"),
					cacheArchiveItem(id = 618007, name = "Duel", air = "2026-09-08 17:44:00"),
				),
				"https://www.rtvs.sk/json/archive5f.json?id=617992" to
					"""{"clip":{"sources":[{"src":"https://cdn.example/morning.m3u8","type":"application/x-mpegurl"}]}}""",
				"https://www.rtvs.sk/json/archive5f.json?id=618007" to
					"""{"clip":{"sources":[{"src":"https://cdn.example/duel.m3u8","type":"application/x-mpegurl"}]}}""",
			),
		)
		val resolver = StvrArchiveResolver(client)
		val channel = directJednotka()

		resolver.resolve(
			channel = channel,
			startsAtMs = 1_788_843_600_000L,
			title = "Ranné správy",
		)
		resolver.resolve(
			channel = channel,
			startsAtMs = 1_788_882_000_000L,
			title = "Duel",
		)

		assertEquals(1, client.requestedUrls.count { it == listingUrl })
	}

	@Test
	fun `refreshes todays JSON archive listing after cache ttl`() = runTest {
		val listingUrl = cacheArchiveApiUrl("2026-09-09")
		val client = SequentialCacheStvrHttpClient(
			listingUrl = listingUrl,
			listings = listOf(
				cacheArchiveListing(),
				cacheArchiveListing(
					cacheArchiveItem(id = 618031, name = "Duel", air = "2026-09-09 10:45:00"),
				),
			),
		)
		var nowMs = 1_788_948_000_000L
		val resolver = StvrArchiveResolver(
			httpClient = client,
			nowMs = { nowMs },
		)
		val programme = ProgramMetadata(
			title = "Duel",
			startsAtMs = 1_788_943_500_000L,
			endsAtMs = 1_788_945_300_000L,
			internetAllowed = true,
		)

		assertFalse(resolver.isAvailable(directJednotka(), programme))
		nowMs += 6 * 60_000L
		assertTrue(resolver.isAvailable(directJednotka(), programme))
		assertEquals(2, client.requestedUrls.count { it == listingUrl })
	}

	private fun directJednotka() = TvChannel(
		storageKey = "jednotka",
		displayName = "JEDNOTKA",
		provider = ChannelProvider.DIRECT,
		providerValue = "https://example.com/live.m3u8",
		archive = ArchiveConfig(ArchiveProvider.STVR, channelId = "1"),
	)
}

private fun cacheArchiveApiUrl(date: String): String =
	"https://www.stvr.sk/json/tv/archiv?e=1&archive=1&d=" + date + "&p=1&l=100&o=desc"

private fun cacheArchiveListing(vararg items: String): String =
	"""{"paging":{"page":"1","size":"100","results":"${items.size}"},"program":[${items.joinToString(",")}]}"""

private fun cacheArchiveItem(
	id: Int,
	name: String,
	air: String,
): String = """{"ID":$id,"series":22948,"name":"$name","air":"$air","license":""}"""

private class RecordingCacheStvrHttpClient(
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

private class SequentialCacheStvrHttpClient(
	private val listingUrl: String,
	private val listings: List<String>,
) : StvrHttpClient {
	val requestedUrls = mutableListOf<String>()
	private var listingIndex = 0

	override suspend fun get(
		url: String,
		headers: Map<String, String>,
	): String {
		requestedUrls += url
		check(url == listingUrl)
		return listings[listingIndex.coerceAtMost(listings.lastIndex)].also {
			listingIndex += 1
		}
	}
}
