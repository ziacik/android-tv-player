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
	fun `reports direct archive item as available`() = runTest {
		val client = AvailabilityStvrHttpClient(
			mapOf(
				"https://www.stvr.sk/televizia/archiv?date=2026-09-08&ord=dt" to listing(
					time = "07:00",
					id = "617992",
					title = "Ranné správy",
				),
			),
		)
		val resolver = StvrArchiveResolver(client)

		val available = resolver.isAvailable(
			channel = directJednotka(),
			program = ProgramMetadata(
				title = "Ranné správy",
				startsAtMs = 1_788_843_600_000L,
				endsAtMs = 1_788_849_000_000L,
				internetAllowed = true,
			),
		)

		assertTrue(available)
	}

	@Test
	fun `reports missing archive item as unavailable`() = runTest {
		val client = AvailabilityStvrHttpClient(
			mapOf(
				"https://www.stvr.sk/televizia/archiv?date=2026-09-08&ord=dt" to listing(
					time = "07:00",
					id = "617992",
					title = "Ranné správy",
				),
			),
		)
		val resolver = StvrArchiveResolver(client)

		val available = resolver.isAvailable(
			channel = directJednotka(),
			program = ProgramMetadata(
				title = "Duel",
				startsAtMs = 1_788_882_000_000L,
				endsAtMs = 1_788_883_800_000L,
				internetAllowed = true,
			),
		)

		assertFalse(available)
	}

	@Test
	fun `reports repeat as available from original airing`() = runTest {
		val client = AvailabilityStvrHttpClient(
			mapOf(
				"https://www.stvr.sk/televizia/archiv?date=2026-09-09&ord=dt" to listing(
					time = "07:00",
					id = "618025",
					title = "Ranné správy",
				),
				"https://www.stvr.sk/televizia/archiv?date=2026-09-08&ord=dt" to listing(
					time = "17:44",
					id = "618007",
					title = "Duel",
				),
			),
		)
		val resolver = StvrArchiveResolver(client)

		val available = resolver.isAvailable(
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

	private fun listing(
		time: String,
		id: String,
		title: String,
	): String = """
		<h2>Jednotka</h2>
		<div class="media">
			<div class="program time--start">$time <span>- 18:12</span></div>
			<a href="/televizia/archiv/14126/$id"><img src="item.jpg"></a>
			<h5><a class="link" title="$title">$title</a></h5>
		</div>
		<h2>Dvojka</h2>
	""".trimIndent()
}

private class AvailabilityStvrHttpClient(
	private val responses: Map<String, String>,
) : StvrHttpClient {
	override suspend fun get(
		url: String,
		headers: Map<String, String>,
	): String = responses.getValue(url)
}
