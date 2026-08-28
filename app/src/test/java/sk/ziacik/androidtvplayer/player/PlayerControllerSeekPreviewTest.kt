package sk.ziacik.androidtvplayer.player

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.StreamResolution
import sk.ziacik.androidtvplayer.resolver.StreamSource

class PlayerControllerSeekPreviewTest {
    @Test
    fun `seek back returns the requested programme clock time`() = runTest {
        val player = RecordingPlayerPort(
            PlaybackSnapshot(
                currentPositionMs = 60_000L,
                durationMs = 100_000L,
                liveOffsetMs = 40_000L,
                isSeekable = true,
                isPlaying = true,
            ),
        )
        val controller = controller(player, nowMs = 1_000_000L)

        val previewTimeMs = controller.seekBack()

        assertEquals(50_000L, player.seekPositionMs)
        assertEquals(950_000L, previewTimeMs)
    }

    @Test
    fun `seek forward clamps to live edge and returns its clock time`() = runTest {
        val player = RecordingPlayerPort(
            PlaybackSnapshot(
                currentPositionMs = 95_000L,
                durationMs = 100_000L,
                liveOffsetMs = 5_000L,
                isSeekable = true,
                isPlaying = true,
            ),
        )
        val controller = controller(player, nowMs = 1_000_000L)

        val previewTimeMs = controller.seekForward()

        assertEquals(100_000L, player.seekPositionMs)
        assertEquals(1_000_000L, previewTimeMs)
    }

    @Test
    fun `non seekable playback has no seek preview`() = runTest {
        val player = RecordingPlayerPort(
            PlaybackSnapshot(
                currentPositionMs = 60_000L,
                durationMs = 100_000L,
                liveOffsetMs = 40_000L,
                isSeekable = false,
                isPlaying = true,
            ),
        )
        val controller = controller(player, nowMs = 1_000_000L)

        assertNull(controller.seekBack())
        assertNull(controller.seekForward())
        assertNull(player.seekPositionMs)
    }

    private fun controller(player: PlayerPort, nowMs: Long) = PlayerController(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        initialChannel = TvChannel.JEDNOTKA,
        resolve = { error("unused") },
        playerPort = player,
        nowMs = { nowMs },
    )

    private class RecordingPlayerPort(
        private val playback: PlaybackSnapshot,
    ) : PlayerPort {
        var seekPositionMs: Long? = null

        override fun snapshot() = playback
        override fun load(loadId: Long, source: StreamSource) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) {
            seekPositionMs = positionMs
        }
        override fun goLive() = Unit
        override fun stop() = Unit
        override fun release() = Unit
        override fun setListener(listener: PlayerPort.Listener) = Unit
    }
}
