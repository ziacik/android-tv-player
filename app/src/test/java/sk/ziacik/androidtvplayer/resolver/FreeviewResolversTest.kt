package sk.ziacik.androidtvplayer.resolver

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.ChannelProvider
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

    @Test
    fun `direct resolver converts AceStream content id to local HLS`() = runTest {
        val channel = TvChannel(
            storageKey = "ace",
            displayName = "ACE",
            provider = ChannelProvider.DIRECT,
            providerValue = "acestream://94c2fd8fb9bc8f2fc71a2cbe9d4b866f227a0209",
        )

        val result = DirectResolver().resolve(channel) as StreamResolution.Playable

        assertEquals(
            "http://127.0.0.1:6878/ace/manifest.m3u8?content_id=94c2fd8fb9bc8f2fc71a2cbe9d4b866f227a0209",
            result.source.url,
        )
        assertEquals(StreamManifest.HLS, result.source.manifest)
    }
}
