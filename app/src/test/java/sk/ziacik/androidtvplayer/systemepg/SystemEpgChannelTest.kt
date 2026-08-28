package sk.ziacik.androidtvplayer.systemepg

import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.ChannelProvider
import sk.ziacik.androidtvplayer.channel.TvChannel

class SystemEpgChannelTest {
    @Test
    fun `maps stable channel identity and catalogue position`() {
        val channel = TvChannel(
            storageKey = "joj",
            displayName = "JOJ",
            provider = ChannelProvider.DIRECT,
            providerValue = "https://example.com/joj.m3u8",
        )

        val published = SystemEpgChannel.from(channel, position = 7)

        assertEquals("joj", published.storageKey)
        assertEquals("JOJ", published.displayName)
        assertEquals("7", published.displayNumber)
    }
}
