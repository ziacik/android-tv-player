package sk.ziacik.androidtvplayer.archive

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.ArchiveConfig
import sk.ziacik.androidtvplayer.channel.ArchiveProvider
import sk.ziacik.androidtvplayer.channel.ChannelProvider
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.StvrHttpClient

class StvrArchiveListingCacheTest {
	@Test
	fun `reuses one STVR day listing for multiple archive programmes`() = runTest {
		val listingUrl = "https://www.stvr.sk/televizia/archiv?date=2026-09-08&ord=dt"
		val client = RecordingStvrHttpClient(
			responses = mapOf(
				listingUrl to """
					<h2>Jednotka</h2>
					<div class="media">
						<div class="program time--start">07:00 <span>- 08:29</span></div>
						<a href="/televizia/archiv/14026/617992"><img src="morning.jpg"></a>
						<h5><a class="link" title="Ranné správy">Ranné správy</a></h5>
					</div>
					<div class="media">
						<div class="program time--start">17:44 <span>- 18:12</span></div>
						<a href="/televizia/archiv/14126/618007"><img src="duel.jpg"></a>
						<h5><a class="link" title="Duel">Duel</a></h5>
					</div>
					<h2>Dvojka</h2>
				""".trimIndent(),
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

	private fun directJednotka() = TvChannel(
		storageKey = "jednotka",
		displayName = "JEDNOTKA",
		provider = ChannelProvider.DIRECT,
		providerValue = "https://example.com/live.m3u8",
		archive = ArchiveConfig(ArchiveProvider.STVR, channelId = "1"),
	)
}

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
