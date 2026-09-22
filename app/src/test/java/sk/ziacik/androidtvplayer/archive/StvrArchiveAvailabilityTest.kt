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
	fun `reports programme as available only when STVR schedule has explicit archive link`() = runTest {
		val scheduleUrl = availabilityScheduleUrl("2026-09-21")
		val client = RecordingStvrHttpClient(
			mapOf(
				scheduleUrl to availabilitySchedule(
					"""<div><span>17:45</span><a href="/televizia/archiv/14126/620530">Duel</a></div>""",
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
	}

	@Test
	fun `does not report programme-only repeat as available`() = runTest {
		val client = RecordingStvrHttpClient(
			mapOf(
				availabilityScheduleUrl("2026-09-21") to availabilitySchedule(
					"""<div><span>10:45</span><a href="/televizia/program/14126/620557">Duel</a></div>""",
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
	fun `does not shift time to find nearby archive item`() = runTest {
		val client = RecordingStvrHttpClient(
			mapOf(
				availabilityScheduleUrl("2026-09-08") to availabilitySchedule(
					"""<div><span>17:44</span><a href="/televizia/archiv/14126/618007">Duel</a></div>""",
				),
			),
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
) : StvrHttpClient {
	override suspend fun get(
		url: String,
		headers: Map<String, String>,
	): String = responses.getValue(url)
}
