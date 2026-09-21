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
				"https://www.stvr.sk/televizia/program/?date=2026-09-08" to listing(
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
				"https://www.stvr.sk/televizia/program/?date=2026-09-08" to listing(
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
	fun `does not treat programme detail link as archive availability`() = runTest {
		val client = AvailabilityStvrHttpClient(
			responses = mapOf(
				"https://www.stvr.sk/televizia/program/?date=2026-09-21" to programOnlyListing(
					time = "10:45",
					id = "620518",
					title = "Duel",
				),
			),
			finalUrls = mapOf(
				"https://www.stvr.sk/televizia/program/14126/620518" to "https://www.stvr.sk/televizia/archiv/14126",
			),
		)
		val resolver = StvrArchiveResolver(client)

		val available = resolver.isAvailable(
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
	fun `refreshes unavailable programme redirect after ttl`() = runTest {
		val listingUrl = "https://www.stvr.sk/televizia/program/?date=2026-09-21"
		val programUrl = "https://www.stvr.sk/televizia/program/14126/620518"
		val client = SequentialRedirectStvrHttpClient(
			listingUrl = listingUrl,
			listing = programOnlyListing(
				time = "10:45",
				id = "620518",
				title = "Duel",
			),
			programUrl = programUrl,
			finalUrls = listOf(
				"https://www.stvr.sk/televizia/archiv/14126",
				"https://www.stvr.sk/televizia/archiv/14126/620518",
			),
		)
		var nowMs = 1_789_984_800_000L
		val resolver = StvrArchiveResolver(
			httpClient = client,
			nowMs = { nowMs },
		)
		val programme = ProgramMetadata(
			title = "Duel",
			startsAtMs = 1_789_980_300_000L,
			endsAtMs = 1_789_982_100_000L,
			internetAllowed = true,
		)

		assertFalse(resolver.isAvailable(directJednotka(), programme))
		nowMs += 6 * 60_000L
		assertTrue(resolver.isAvailable(directJednotka(), programme))
		assertTrue(client.finalUrlRequests == 2)
	}

	@Test
	fun `treats different broadcasts as available when they redirect to the same archive episode`() = runTest {
		val firstProgramUrl = "https://www.stvr.sk/televizia/program/14126/700001"
		val secondProgramUrl = "https://www.stvr.sk/televizia/program/14126/700002"
		val archiveUrl = "https://www.stvr.sk/televizia/archiv/14126/699999"
		val client = AvailabilityStvrHttpClient(
			responses = mapOf(
				"https://www.stvr.sk/televizia/program/?date=2026-09-22" to """
					<h2>Jednotka</h2>
					<div class="media">
						<div class="program time--start">10:45 <span>- 11:15</span></div>
						<h5><a href="/televizia/program/14126/700001">Duel</a></h5>
					</div>
					<div class="media">
						<div class="program time--start">17:45 <span>- 18:15</span></div>
						<h5><a href="/televizia/program/14126/700002">Duel</a></h5>
					</div>
					<h2>Dvojka</h2>
				""".trimIndent(),
			),
			finalUrls = mapOf(
				firstProgramUrl to archiveUrl,
				secondProgramUrl to archiveUrl,
			),
		)
		val resolver = StvrArchiveResolver(client)

		assertTrue(
			resolver.isAvailable(
				directJednotka(),
				ProgramMetadata(
					title = "Duel",
					startsAtMs = 1_790_066_700_000L,
					endsAtMs = 1_790_068_500_000L,
					internetAllowed = true,
				),
			),
		)
		assertTrue(
			resolver.isAvailable(
				directJednotka(),
				ProgramMetadata(
					title = "Duel",
					startsAtMs = 1_790_091_900_000L,
					endsAtMs = 1_790_093_700_000L,
					internetAllowed = true,
				),
			),
		)
	}

	@Test
	fun `reports repeat as available from original airing`() = runTest {
		val client = AvailabilityStvrHttpClient(
			mapOf(
				"https://www.stvr.sk/televizia/program/?date=2026-09-09" to listing(
					time = "07:00",
					id = "618025",
					title = "Ranné správy",
				),
				"https://www.stvr.sk/televizia/program/?date=2026-09-08" to listing(
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
			<h5><a href="/televizia/program/14126/$id">$title</a></h5>
		</div>
		<h2>Dvojka</h2>
	""".trimIndent()

	private fun programOnlyListing(
		time: String,
		id: String,
		title: String,
	): String = """
		<h2>Jednotka</h2>
		<div class="media">
			<div class="program time--start">$time <span>- 11:15</span></div>
			<h5><a href="/televizia/program/14126/$id">$title</a></h5>
		</div>
		<h2>Dvojka</h2>
	""".trimIndent()
}

private class AvailabilityStvrHttpClient(
	private val responses: Map<String, String>,
	private val finalUrls: Map<String, String> = emptyMap(),
) : StvrHttpClient {
	override suspend fun get(
		url: String,
		headers: Map<String, String>,
	): String = responses.getValue(url)

	override suspend fun finalUrl(
		url: String,
		headers: Map<String, String>,
	): String = finalUrls[url] ?: url.replace("/televizia/program/", "/televizia/archiv/")
}


private class SequentialRedirectStvrHttpClient(
	private val listingUrl: String,
	private val listing: String,
	private val programUrl: String,
	private val finalUrls: List<String>,
) : StvrHttpClient {
	var finalUrlRequests: Int = 0
	private var finalUrlIndex: Int = 0

	override suspend fun get(
		url: String,
		headers: Map<String, String>,
	): String {
		check(url == listingUrl)
		return listing
	}

	override suspend fun finalUrl(
		url: String,
		headers: Map<String, String>,
	): String {
		check(url == programUrl)
		finalUrlRequests += 1
		return finalUrls[finalUrlIndex.coerceAtMost(finalUrls.lastIndex)].also {
			finalUrlIndex += 1
		}
	}
}
