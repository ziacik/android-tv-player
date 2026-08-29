package sk.ziacik.androidtvplayer.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.StreamResolution
import sk.ziacik.androidtvplayer.resolver.StreamSource

@OptIn(ExperimentalCoroutinesApi::class)
class StreamHostStateTest {
    @Test
    fun `preparing state exposes playlist host before media is ready`() = runTest {
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { playable("https://media.example.com/live.m3u8") },
            playerPort = player,
        )

        controller.start()
        advanceUntilIdle()

        assertEquals(
            "media.example.com",
            (controller.state.value as PlayerUiState.Preparing).streamHost,
        )
        controller.release()
    }

    @Test
    fun `playback error keeps playlist host visible`() = runTest {
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { playable("http://185.188.188.237:5000/live/paprika/playlist.m3u8") },
            playerPort = player,
        )

        controller.start()
        advanceUntilIdle()
        player.listener.onError(player.latestLoadId, "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT")
        runCurrent()

        assertEquals(
            "185.188.188.237",
            (controller.state.value as PlayerUiState.Error).streamHost,
        )
        controller.release()
    }

    private fun playable(url: String) = StreamResolution.Playable(
        program = ProgramMetadata(
            title = "Program",
            startsAtMs = 1_000L,
            endsAtMs = 100_000L,
            internetAllowed = true,
        ),
        source = StreamSource(url = url, userAgent = "ua"),
    )

    private class FakePlayerPort : PlayerPort {
        lateinit var listener: PlayerPort.Listener
        private val loadIds = mutableListOf<Long>()

        val latestLoadId: Long
            get() = loadIds.last()

        override fun snapshot() = PlaybackSnapshot(
            currentPositionMs = 0L,
            durationMs = null,
            liveOffsetMs = null,
            isSeekable = false,
            isPlaying = false,
        )

        override fun load(loadId: Long, source: StreamSource) {
            loadIds += loadId
        }

        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun goLive() = Unit
        override fun stop() = Unit
        override fun release() = Unit

        override fun setListener(listener: PlayerPort.Listener) {
            this.listener = listener
        }
    }
}
