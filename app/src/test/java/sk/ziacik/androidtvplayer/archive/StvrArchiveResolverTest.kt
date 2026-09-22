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
	fun `resolves stream from exact STVR programme redirect`() = runTest {
		val scheduleUrl = resolverScheduleUrl("2026-09-22")
		val programmeUrl = "https://www.stvr.sk/televizia/program/14029/620563"
		val client = ResolverStvrHttpClient(
			responses = mapOf(
				scheduleUrl to resolverSchedule(
					jednotka = """
						<div><span>15:15</span><a href="/televizia/program/14029/620563">Doktor z hôr</a></div>
					""".trimIndent(),
				),
				"https://www.rtvs.sk/json/archive5f.json?id=620563" to
					"""{"clip":{"sources":[{"src":"https://cdn.example/doctor.m3u8","type":"application/x-mpegurl"}]}}""",
			),
			finalUrls = mapOf(
				programmeUrl to "https://www.stvr.sk/televizia/archiv/14029/620563",
			),
		)

		val result = StvrArchiveResolver(client).resolve(
			channel = directJednotka(),
			startsAtMs = 1_790_082_900_000L,
		)

		assertEquals("https://cdn.example/doctor.m3u8", result.url)
		assertEquals(listOf(scheduleUrl, "https://www.rtvs.sk/json/archive5f.json?id=620563"), client.requestedUrls)
		assertEquals(listOf(programmeUrl), client.requestedFinalUrls)
	}

	@Test
	fun `does not steal archive link from following programme`() = runTest {
		val scheduleUrl = resolverScheduleUrl("2026-09-22")
		val snackProgrammeUrl = "https://www.stvr.sk/televizia/program/14658/620562"
		val doctorProgrammeUrl = "https://www.stvr.sk/televizia/program/14029/620563"
		val client = ResolverStvrHttpClient(
			responses = mapOf(
				scheduleUrl to resolverSchedule(
					jednotka = """
						<div><span>14:50</span><a href="/televizia/program/14658/620562">Zdravá maškrta</a></div>
						<div><span>15:15</span><a href="/televizia/program/14029/620563">Doktor z hôr</a></div>
					""".trimIndent(),
				),
			),
			finalUrls = mapOf(
				snackProgrammeUrl to "https://www.stvr.sk/televizia/archiv/14658",
				doctorProgrammeUrl to "https://www.stvr.sk/televizia/archiv/14029/620563",
			),
		)

		try {
			StvrArchiveResolver(client).resolve(
				channel = directJednotka(),
				startsAtMs = 1_790_081_400_000L,
			)
			fail("Expected Zdravá maškrta without concrete archive episode to fail")
		} catch (error: StreamResolveException) {
			val message = error.message.orEmpty()
			assertTrue(message.contains("expectedTime=14:50"))
			assertTrue(message.contains("programmeUrl=$snackProgrammeUrl"))
			assertTrue(message.contains("finalUrl=https://www.stvr.sk/televizia/archiv/14658"))
		}

		assertEquals(listOf(scheduleUrl), client.requestedUrls)
		assertEquals(listOf(snackProgrammeUrl), client.requestedFinalUrls)
	}

	@Test
	fun `does not guess archive when exact STVR time is absent`() = runTest {
		val scheduleUrl = resolverScheduleUrl("2026-09-08")
		val client = ResolverStvrHttpClient(
			responses = mapOf(
				scheduleUrl to resolverSchedule(
					jednotka = """
						<div><span>17:44</span><a href="/televizia/program/14126/618007">Duel</a></div>
					""".trimIndent(),
				),
			),
			finalUrls = emptyMap(),
		)

		try {
			StvrArchiveResolver(client).resolve(
				channel = directJednotka(),
				startsAtMs = 1_788_882_000_000L,
			)
			fail("Expected exact-time lookup to fail")
		} catch (error: StreamResolveException) {
			assertTrue(error.message.orEmpty().contains("expectedTime=17:40"))
			assertTrue(error.message.orEmpty().contains("exactTimeMatches=0"))
		}
		assertEquals(emptyList<String>(), client.requestedFinalUrls)
	}

	@Test
	fun `uses only configured STVR channel section`() = runTest {
		val scheduleUrl = resolverScheduleUrl("2026-09-08")
		val dvojkaProgrammeUrl = "https://www.stvr.sk/televizia/program/2/700002"
		val client = ResolverStvrHttpClient(
			responses = mapOf(
				scheduleUrl to resolverSchedule(
					jednotka = """<div><span>17:44</span><a href="/televizia/program/1/111111">Jednotka</a></div>""",
					dvojka = """<div><span>17:44</span><a href="/televizia/program/2/700002">Dvojka</a></div>""",
				),
				"https://www.rtvs.sk/json/archive5f.json?id=700002" to
					"""{"clip":{"sources":[{"src":"https://cdn.example/dvojka.m3u8","type":"application/x-mpegurl"}]}}""",
			),
			finalUrls = mapOf(
				dvojkaProgrammeUrl to "https://www.stvr.sk/televizia/archiv/2/700002",
			),
		)

		val result = StvrArchiveResolver(client).resolve(
			channel = TvChannel(
				storageKey = "dvojka",
				displayName = "DVOJKA",
				provider = ChannelProvider.DIRECT,
				providerValue = "https://example.com/live.m3u8",
				archive = ArchiveConfig(ArchiveProvider.STVR, channelId = "2"),
			),
			startsAtMs = 1_788_882_240_000L,
		)

		assertEquals("https://cdn.example/dvojka.m3u8", result.url)
		assertEquals(listOf(dvojkaProgrammeUrl), client.requestedFinalUrls)
	}

	private fun directJednotka() = TvChannel(
		storageKey = "jednotka",
		displayName = "JEDNOTKA",
		provider = ChannelProvider.DIRECT,
		providerValue = "https://example.com/live.m3u8",
		archive = ArchiveConfig(ArchiveProvider.STVR, channelId = "1"),
	)
}

private fun resolverScheduleUrl(date: String): String =
	"https://www.stvr.sk/televizia/program/?date=$date"

private fun resolverSchedule(
	jednotka: String = "",
	dvojka: String = "",
): String =
	"""<html><h2>Jednotka</h2>$jednotka<h2>Dvojka</h2>$dvojka<h2>:24</h2><h2>Šport</h2></html>"""

private class ResolverStvrHttpClient(
	private val responses: Map<String, String>,
	private val finalUrls: Map<String, String>,
) : StvrHttpClient {
	val requestedUrls = mutableListOf<String>()
	val requestedFinalUrls = mutableListOf<String>()

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
	): String {
		requestedFinalUrls += url
		return finalUrls.getValue(url)
	}
}
