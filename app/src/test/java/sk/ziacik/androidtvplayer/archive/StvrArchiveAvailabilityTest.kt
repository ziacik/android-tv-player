package sk.ziacik.androidtvplayer.archive

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.ArchiveConfig
import sk.ziacik.androidtvplayer.channel.ArchiveProvider
import sk.ziacik.androidtvplayer.channel.ChannelProvider
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.StvrHttpClient

class StvrArchiveAvailabilityTest {
	@Test
	fun `reports Duel archive listed by JSON API as available`() = runTest {
		val apiUrl = availabilityArchiveApiUrl("2026-09-21")
		val client = RecordingStvrHttpClient(
			mapOf(
				apiUrl to availabilityArchiveListing(
					availabilityArchiveItem(id = 620530, name = "Duel", air = "2026-09-21 17:45:00"),
				),
			),
		)

		val available = StvrArchiveResolver(client).isAvailable(
			channel = directJednotka(),
			program = ProgramMetadata(
				title = "Duel",
				startsAtMs = 1_790_005_500_000L,
				endsAtMs = 1_790_007_300_000L,
				internetAllowed = true,
			),
		)

		assertTrue(available)
		assertTrue(client.requestedUrls == listOf(apiUrl))
	}

	@Test
	fun `does not report morning Duel as available when JSON API only contains evening Duel`() = runTest {
		val client = RecordingStvrHttpClient(
			mapOf(
				availabilityArchiveApiUrl("2026-09-21") to availabilityArchiveListing(
					availabilityArchiveItem(id = 620530, name = "Duel", air = "2026-09-21 17:45:00"),
				),
			),
		)

		val available = StvrArchiveResolver(client).isAvailable(
			channel = directJednotka(),
			program = ProgramMetadata(
				title = "Duel",
				startsAtMs = 1_789_980_300_000L,
				endsAtMs = 1_789_982_100_000L,
				internetAllowed = true,
			),
		)

		assertFalse(available)
	}

	@Test
	fun `does not report Profesionali as available when missing from JSON archive API`() = runTest {
		val client = RecordingStvrHttpClient(
			mapOf(
				availabilityArchiveApiUrl("2026-09-21") to availabilityArchiveListing(
					availabilityArchiveItem(id = 620530, name = "Duel", air = "2026-09-21 17:45:00"),
				),
			),
		)

		val available = StvrArchiveResolver(client).isAvailable(
			channel = directJednotka(),
			program = ProgramMetadata(
				title = "Profesionáli IV",
				startsAtMs = 1_789_977_300_000L,
				endsAtMs = 1_789_980_300_000L,
				internetAllowed = true,
			),
		)

		assertFalse(available)
	}

	@Test
	fun `reports repeat as available from original airing JSON archive date`() = runTest {
		val client = RecordingStvrHttpClient(
			mapOf(
				availabilityArchiveApiUrl("2026-09-09") to availabilityArchiveListing(),
				availabilityArchiveApiUrl("2026-09-08") to availabilityArchiveListing(
					availabilityArchiveItem(id = 618007, name = "Duel", air = "2026-09-08 17:44:00"),
				),
			),
		)

		val available = StvrArchiveResolver(client).isAvailable(
			channel = directJednotka(),
			program = ProgramMetadata(
				title = "Duel",
				startsAtMs = 1_788_943_500_000L,
				endsAtMs = 1_788_945_300_000L,
				internetAllowed = true,
				archiveOriginalStartsAtMs = 1_788_882_300_000L,
			),
		)

		assertTrue(available)
	}

	private fun directJednotka() = TvChannel(
		storageKey = "jednotka",
		displayName = "JEDNOTKA",
		provider = ChannelProvider.DIRECT,
		providerValue = "https://example.com/live.m3u8",
		archive = ArchiveConfig(ArchiveProvider.STVR, channelId = "1"),
	)
}

private fun availabilityArchiveApiUrl(date: String): String =
	"https://www.stvr.sk/json/tv/archiv?e=1&archive=1&d=" + date + "&p=1&l=100&o=desc"

private fun availabilityArchiveListing(vararg items: String): String =
	"""{"paging":{"page":"1","size":"100","results":"${items.size}"},"program":[${items.joinToString(",")}]}"""

private fun availabilityArchiveItem(
	id: Int,
	name: String,
	air: String,
): String = """{"ID":$id,"series":22948,"name":"$name","air":"$air","license":""}"""

private class RecordingStvrHttpClient(
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
