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
	fun `reuses one STVR programme schedule for multiple archive programmes`() = runTest {
		val scheduleUrl = cacheScheduleUrl("2026-09-08")
		val morningUrl = "https://www.stvr.sk/televizia/program/22948/617992"
		val duelUrl = "https://www.stvr.sk/televizia/program/14126/618007"
		val client = RecordingCacheStvrHttpClient(
			responses = mapOf(
				scheduleUrl to cacheSchedule(
					"""
						<div><span>07:00</span><a href="/televizia/program/22948/617992">Ranné správy</a></div>
						<div><span>17:44</span><a href="/televizia/program/14126/618007">Duel</a></div>
					""".trimIndent(),
				),
				"https://www.rtvs.sk/json/archive5f.json?id=617992" to
					"""{"clip":{"sources":[{"src":"https://cdn.example/morning.m3u8","type":"application/x-mpegurl"}]}}""",
				"https://www.rtvs.sk/json/archive5f.json?id=618007" to
					"""{"clip":{"sources":[{"src":"https://cdn.example/duel.m3u8","type":"application/x-mpegurl"}]}}""",
			),
			finalUrls = mapOf(
				morningUrl to "https://www.stvr.sk/televizia/archiv/22948/617992",
				duelUrl to "https://www.stvr.sk/televizia/archiv/14126/618007",
			),
		)
		val resolver = StvrArchiveResolver(client)
		val channel = directJednotka()

		resolver.resolve(channel = channel, startsAtMs = 1_788_843_600_000L)
		resolver.resolve(channel = channel, startsAtMs = 1_788_882_240_000L)

		assertEquals(1, client.requestedUrls.count { it == scheduleUrl })
	}

	@Test
	fun `refreshes todays STVR programme schedule after cache ttl`() = runTest {
		val scheduleUrl = cacheScheduleUrl("2026-09-09")
		val programmeUrl = "https://www.stvr.sk/televizia/program/14126/618031"
		val client = SequentialCacheStvrHttpClient(
			scheduleUrl = scheduleUrl,
			schedules = listOf(
				cacheSchedule("""<div><span>10:45</span><a href="/televizia/program/14126/618031">Duel</a></div>"""),
				cacheSchedule("""<div><span>10:45</span><a href="/televizia/program/14126/618031">Duel</a></div>"""),
			),
			finalUrls = listOf(
				"https://www.stvr.sk/televizia/archiv/14126",
				"https://www.stvr.sk/televizia/archiv/14126/618031",
			),
			programmeUrl = programmeUrl,
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
		assertEquals(2, client.requestedUrls.count { it == scheduleUrl })
	}

	private fun directJednotka() = TvChannel(
		storageKey = "jednotka",
		displayName = "JEDNOTKA",
		provider = ChannelProvider.DIRECT,
		providerValue = "https://example.com/live.m3u8",
		archive = ArchiveConfig(ArchiveProvider.STVR, channelId = "1"),
	)
}

private fun cacheScheduleUrl(date: String): String =
	"https://www.stvr.sk/televizia/program/?date=$date"

private fun cacheSchedule(jednotka: String): String =
	"""<html><h2>Jednotka</h2>$jednotka<h2>Dvojka</h2></html>"""

private class RecordingCacheStvrHttpClient(
	private val responses: Map<String, String>,
	private val finalUrls: Map<String, String>,
) : StvrHttpClient {
	val requestedUrls = mutableListOf<String>()

	override suspend fun get(
		url: String,
		headers: Map<String, String>,
	): String {
		requestedUrls += url
		return responses.getValue(url)
	}

	override suspend fun finalUrl(
		url: String,
		headers: Map<String, String>,
	): String = finalUrls.getValue(url)
}

private class SequentialCacheStvrHttpClient(
	private val scheduleUrl: String,
	private val schedules: List<String>,
	private val finalUrls: List<String>,
	private val programmeUrl: String,
) : StvrHttpClient {
	val requestedUrls = mutableListOf<String>()
	private var scheduleIndex = 0
	private var finalUrlIndex = 0

	override suspend fun get(
		url: String,
		headers: Map<String, String>,
	): String {
		requestedUrls += url
		check(url == scheduleUrl)
		return schedules[scheduleIndex.coerceAtMost(schedules.lastIndex)].also {
			scheduleIndex += 1
		}
	}

	override suspend fun finalUrl(
		url: String,
		headers: Map<String, String>,
	): String {
		check(url == programmeUrl)
		return finalUrls[finalUrlIndex.coerceAtMost(finalUrls.lastIndex)].also {
			finalUrlIndex += 1
		}
	}
}
