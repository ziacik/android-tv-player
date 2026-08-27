package sk.ziacik.androidtvplayer.resolver

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel

class FreeviewResolversTest {
    @Test
    fun `JOJ Tivio MPD source is marked as DASH`() = runTest {
        val result = JojResolver(
            object : FreeviewHttpClient {
                override suspend fun get(url: String, headers: Map<String, String>): String = error("unused")

                override suspend fun postJson(
                    url: String,
                    body: String,
                    headers: Map<String, String>,
                ): String = """{"result":{"url":"https://cdn.example/joj.mpd"}}"""
            },
        ).resolve(TvChannel.JOJ) as StreamResolution.Playable

        assertEquals(StreamManifest.DASH, result.source.manifest)
    }

    @Test
    fun `JOJ Tivio HLS source is marked as HLS`() = runTest {
        val result = JojResolver(
            object : FreeviewHttpClient {
                override suspend fun get(url: String, headers: Map<String, String>): String = error("unused")

                override suspend fun postJson(
                    url: String,
                    body: String,
                    headers: Map<String, String>,
                ): String = """{"result":{"url":"https://cdn.example/joj.m3u8"}}"""
            },
        ).resolve(TvChannel.JOJ) as StreamResolution.Playable

        assertEquals("https://cdn.example/joj.m3u8", result.source.url)
        assertEquals(StreamManifest.HLS, result.source.manifest)
    }
}
