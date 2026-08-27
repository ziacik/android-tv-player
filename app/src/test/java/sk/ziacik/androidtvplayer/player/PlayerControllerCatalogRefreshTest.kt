package sk.ziacik.androidtvplayer.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.ChannelProvider
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.StreamResolution
import sk.ziacik.androidtvplayer.resolver.StreamSource

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerCatalogRefreshTest {
    @Test
    fun `retry resolves refreshed channel instance with the same storage key`() = runTest {
        val originalEntries = TvChannel.entries
        val oldChannel = direct("current", "https://example.com/old.m3u8")
        val refreshedChannel = direct("current", "https://example.com/new.m3u8")
        val resolvedUrls = mutableListOf<String?>()
        val controller = PlayerController(
            scope = this,
            initialChannel = oldChannel,
            resolve = { channel ->
                resolvedUrls += channel.providerValue
                StreamResolution.Playable(
                    ProgramMetadata(channel.displayName, null, null, true),
                    StreamSource(requireNotNull(channel.providerValue), "test"),
                )
            },
            playerPort = NoopPlayerPort(),
        )

        try {
            TvChannel.setRuntimeEntries(listOf(oldChannel))
            controller.start()
            advanceUntilIdle()

            TvChannel.setRuntimeEntries(listOf(refreshedChannel))
            controller.retry()
            advanceUntilIdle()

            assertEquals(
                listOf("https://example.com/old.m3u8", "https://example.com/new.m3u8"),
                resolvedUrls,
            )
        } finally {
            controller.release()
            TvChannel.setRuntimeEntries(originalEntries)
        }
    }

    @Test
    fun `existing channel navigates refreshed entries by storage key`() {
        val originalEntries = TvChannel.entries
        val oldMiddle = direct("middle", "https://example.com/old.m3u8")
        val first = direct("first", "https://example.com/first.m3u8")
        val refreshedMiddle = direct("middle", "https://example.com/new.m3u8")
        val last = direct("last", "https://example.com/last.m3u8")

        try {
            TvChannel.setRuntimeEntries(listOf(first, refreshedMiddle, last))

            assertEquals(last, oldMiddle.next())
            assertEquals(first, oldMiddle.previous())
        } finally {
            TvChannel.setRuntimeEntries(originalEntries)
        }
    }

    private fun direct(id: String, url: String) = TvChannel(
        storageKey = id,
        displayName = id.uppercase(),
        provider = ChannelProvider.DIRECT,
        providerValue = url,
    )

    private class NoopPlayerPort : PlayerPort {
        override fun snapshot() = PlaybackSnapshot(0L, null, null, false, false)
        override fun load(loadId: Long, source: StreamSource) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun goLive() = Unit
        override fun stop() = Unit
        override fun release() = Unit
        override fun setListener(listener: PlayerPort.Listener) = Unit
    }
}
