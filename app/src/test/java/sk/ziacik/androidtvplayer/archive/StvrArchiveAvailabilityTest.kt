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
	fun `reports Duel present in STVR archive API as available`() = runTest {
		val client = AvailabilityStvrHttpClient(
			mapOf(
				archiveApiUrl("2026-09-21") to archiveApi(
					archiveItem(620530, "Duel", "2026-09-21 17:45:00"),
				),
			),
		)
		val resolver = StvrArchiveResolver(client)

		assertTrue(
			resolver.isAvailable(
				channel = directJednotka(),
				program = ProgramMetadata(
					title = "Duel",
					startsAtMs = 1_790_005_500_000L,
					endsAtMs = 1_790_007_300_000L,
					internetAllowed = true,
				),
			),
		)
	}

	@Test
	fun `reports Duel broadcast missing from STVR archive API as unavailable`() = runTest {
		val client = AvailabilityStvrHttpClient(
			mapOf(
				archiveApiUrl("2026-09-21") to archiveApi(
					archiveItem(620530, "Duel", "2026-09-21 17:45:00"),
				),
			),
		)
		val resolver = StvrArchiveResolver(client)

		assertFalse(
			resolver.isAvailable(
				channel = directJednotka(),
				program = ProgramMetadata(
					title = "Duel",
					startsAtMs = 1_789_980_300_000L,
					endsAtMs = 1_789_982_100_000L,
					internetAllowed = true,
				),
			),
		)
	}

	@Test
	fun `reports programme omitted by STVR archive API as unavailable`() = runTest {
		val client = AvailabilityStvrHttpClient(
			mapOf(
				archiveApiUrl("2026-09-21") to archiveApi(
					archiveItem(620530, "Duel", "2026-09-21 17:45:00"),
				),
			),
		)
		val resolver = StvrArchiveResolver(client)

		assertFalse(
			resolver.isAvailable(
				channel = directJednotka(),
				program = ProgramMetadata(
					title = "Profesionáli IV",
					startsAtMs = 1_789_977_300_000L,
					endsAtMs = 1_789_980_300_000L,
					internetAllowed = true,
				),
			),
		)
	}

	@Test
	fun `reports repeat as available from original airing archive date`() = runTest {
		val client = AvailabilityStvrHttpClient(
			mapOf(
				archiveApiUrl("2026-09-09") to archiveApi(),
				archiveApiUrl("2026-09-08") to archiveApi(
					archiveItem(618007, "Duel", "2026-09-08 17:44:00"),
				),
			),
		)
		val resolver = StvrArchiveResolver(client)

		assertTrue(
			resolver.isAvailable(
				channel = directJednotka(),
				program = ProgramMetadata(
					title = "Duel",
					startsAtMs = 1_788_943_500_000L,
					endsAtMs = 1_788_945_300_000L,
					internetAllowed = true,
					archiveOriginalStartsAtMs = 1_788_882_300_000L,
				),
			),
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

private class AvailabilityStvrHttpClient(
	private val responses: Map<String, String>,
) : StvrHttpClient {
	override suspend fun get(
		url: String,
		headers: Map<String, String>,
	): String = responses.getValue(url)
}
