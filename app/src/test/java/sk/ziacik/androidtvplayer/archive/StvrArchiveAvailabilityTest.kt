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
	fun `reports programme available when exact programme redirects to archive episode`() = runTest {
		val programmeUrl = "https://www.stvr.sk/televizia/program/14126/620530"
		val client = RecordingStvrHttpClient(
			responses = mapOf(
				availabilityScheduleUrl("2026-09-21") to availabilitySchedule(
					"""<div><span>17:45</span><a href="/televizia/program/14126/620530">Duel</a></div>""",
				),
			),
			finalUrls = mapOf(
				programmeUrl to "https://www.stvr.sk/televizia/archiv/14126/620530",
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
	}

	@Test
	fun `does not report series-only programme redirect as available`() = runTest {
		val programmeUrl = "https://www.stvr.sk/televizia/program/14658/620562"
		val client = RecordingStvrHttpClient(
			responses = mapOf(
				availabilityScheduleUrl("2026-09-22") to availabilitySchedule(
					"""
						<div><span>14:50</span><a href="/televizia/program/14658/620562">Zdravá maškrta</a></div>
						<div><span>15:15</span><a href="/televizia/program/14029/620563">Doktor z hôr</a></div>
					""".trimIndent(),
				),
			),
			finalUrls = mapOf(
				programmeUrl to "https://www.stvr.sk/televizia/archiv/14658",
				"https://www.stvr.sk/televizia/program/14029/620563" to
					"https://www.stvr.sk/televizia/archiv/14029/620563",
			),
		)

		val available = StvrArchiveResolver(client).isAvailable(
			channel = directJednotka(),
			program = ProgramMetadata(
				title = "Zdravá maškrta (19)",
				startsAtMs = 1_790_081_400_000L,
				endsAtMs = 1_790_082_900_000L,
				internetAllowed = true,
			),
		)

		assertFalse(available)
		assertTrue(client.requestedFinalUrls == listOf(programmeUrl))
	}

	@Test
	fun `does not shift exact programme time`() = runTest {
		val client = RecordingStvrHttpClient(
			responses = mapOf(
				availabilityScheduleUrl("2026-09-08") to availabilitySchedule(
					"""<div><span>17:44</span><a href="/televizia/program/14126/618007">Duel</a></div>""",
				),
			),
			finalUrls = emptyMap(),
		)

		val available = StvrArchiveResolver(client).isAvailable(
			channel = directJednotka(),
			program = ProgramMetadata(
				title = "Duel",
				startsAtMs = 1_788_882_000_000L,
				endsAtMs = 1_788_884_000_000L,
				internetAllowed = true,
			),
		)

		assertFalse(available)
		assertTrue(client.requestedFinalUrls.isEmpty())
	}

	private fun directJednotka() = TvChannel(
		storageKey = "jednotka",
		displayName = "JEDNOTKA",
		provider = ChannelProvider.DIRECT,
		providerValue = "https://example.com/live.m3u8",
		archive = ArchiveConfig(ArchiveProvider.STVR, channelId = "1"),
	)
}

private fun availabilityScheduleUrl(date: String): String =
	"https://www.stvr.sk/televizia/program/?date=$date"

private fun availabilitySchedule(jednotka: String): String =
	"""<html><h2>Jednotka</h2>$jednotka<h2>Dvojka</h2></html>"""

private class RecordingStvrHttpClient(
	private val responses: Map<String, String>,
	private val finalUrls: Map<String, String>,
) : StvrHttpClient {
	val requestedFinalUrls = mutableListOf<String>()

	override suspend fun get(
		url: String,
		headers: Map<String, String>,
	): String = responses.getValue(url)

	override suspend fun finalUrl(
		url: String,
		headers: Map<String, String>,
	): String {
		requestedFinalUrls += url
		return finalUrls.getValue(url)
	}
}
