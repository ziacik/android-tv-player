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
	fun `resolves stream only from explicit archive link at exact programme time`() = runTest {
		val scheduleUrl = resolverScheduleUrl("2026-09-08")
		val client = ResolverStvrHttpClient(
			mapOf(
				scheduleUrl to resolverSchedule(
					jednotka = """
						<div><span>07:00</span><a href="/televizia/archiv/22948/617992">Ranné správy</a></div>
					""".trimIndent(),
				),
				"https://www.rtvs.sk/json/archive5f.json?id=617992" to
					"""{"clip":{"sources":[{"src":"https://cdn.example/archive.m3u8","type":"application/x-mpegurl"}]}}""",
			),
		)

		val result = StvrArchiveResolver(client).resolve(
			channel = directJednotka(),
			startsAtMs = 1_788_843_600_000L,
		)

		assertEquals("https://cdn.example/archive.m3u8", result.url)
		assertEquals(
			listOf(
				scheduleUrl,
				"https://www.rtvs.sk/json/archive5f.json?id=617992",
			),
			client.requestedUrls,
		)
	}

	@Test
	fun `does not guess archive when STVR start differs from EPG start`() = runTest {
		val scheduleUrl = resolverScheduleUrl("2026-09-08")
		val client = ResolverStvrHttpClient(
			mapOf(
				scheduleUrl to resolverSchedule(
					jednotka = """
						<div><span>17:44</span><a href="/televizia/archiv/14126/618007">Duel</a></div>
					""".trimIndent(),
				),
			),
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
		assertEquals(listOf(scheduleUrl), client.requestedUrls)
	}

	@Test
	fun `does not resolve repeat when STVR exposes only programme link`() = runTest {
		val scheduleUrl = resolverScheduleUrl("2026-09-21")
		val client = ResolverStvrHttpClient(
			mapOf(
				scheduleUrl to resolverSchedule(
					jednotka = """
						<div>
							<span>10:45</span>
							<a href="/televizia/program/14126/620557">Duel</a>
						</div>
						<div>
							<span>11:15</span>
							<a href="/televizia/archiv/20000/999999">Nasledujúci program</a>
						</div>
					""".trimIndent(),
				),
			),
		)

		try {
			StvrArchiveResolver(client).resolve(
				channel = directJednotka(),
				startsAtMs = 1_789_980_300_000L,
			)
			fail("Expected repeat without explicit archive link to fail")
		} catch (error: StreamResolveException) {
			assertTrue(error.message.orEmpty().contains("expectedTime=10:45"))
			assertTrue(error.message.orEmpty().contains("archiveIds="))
		}
		assertEquals(listOf(scheduleUrl), client.requestedUrls)
	}

	@Test
	fun `uses only the configured STVR channel section`() = runTest {
		val scheduleUrl = resolverScheduleUrl("2026-09-08")
		val client = ResolverStvrHttpClient(
			mapOf(
				scheduleUrl to resolverSchedule(
					jednotka = """<div><span>17:44</span><a href="/televizia/archiv/1/111111">Jednotka</a></div>""",
					dvojka = """<div><span>17:44</span><a href="/televizia/archiv/2/700002">Dvojka</a></div>""",
				),
				"https://www.rtvs.sk/json/archive5f.json?id=700002" to
					"""{"clip":{"sources":[{"src":"https://cdn.example/dvojka.m3u8","type":"application/x-mpegurl"}]}}""",
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
