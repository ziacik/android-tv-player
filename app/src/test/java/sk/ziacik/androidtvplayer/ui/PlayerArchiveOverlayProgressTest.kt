package sk.ziacik.androidtvplayer.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.player.PlaybackSnapshot
import sk.ziacik.androidtvplayer.player.PlayerController
import sk.ziacik.androidtvplayer.player.PlayerPort
import sk.ziacik.androidtvplayer.player.PlayerUiState
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.StreamResolution
import sk.ziacik.androidtvplayer.resolver.StreamSource

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerArchiveOverlayProgressTest {
    @Test
    fun `archive overlay follows playback position instead of wall clock`() = runTest {
        val player = FakePlayerPort()
        val archiveProgramme = ProgramMetadata(
            title = "Duel",
            startsAtMs = 10_000L,
            endsAtMs = 110_000L,
            internetAllowed = true,
        )
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = {
                StreamResolution.Playable(
                    program = ProgramMetadata("Live", 900_000L, 1_100_000L, true),
                    source = StreamSource("https://cdn.example/live.m3u8", "ua"),
                )
            },
            resolveArchive = { _, _ ->
                StreamSource("https://cdn.example/archive.m3u8", "ua")
            },
            playerPort = player,
            nowMs = { 1_000_000L },
        )

        controller.start()
        advanceUntilIdle()
        controller.playArchive(TvChannel.JEDNOTKA, archiveProgramme)
        advanceUntilIdle()

        player.positionMs = 40_000L
        controller.refreshPlaybackSnapshot()
        val ready = controller.state.value as PlayerUiState.Ready
        val model = PlayerOverlayModel.from(ready, nowMs = 1_000_000L)

        assertEquals(0.4f, model.progress!!, 0.0001f)
        assertEquals(10_000L, model.programmeStartMs)
        assertEquals(50_000L, model.programmeNowMs)
        assertEquals(110_000L, model.programmeEndMs)
        assertFalse(model.isLive)
    }

    private class FakePlayerPort : PlayerPort {
        var positionMs = 0L
        private lateinit var listener: PlayerPort.Listener

        override fun snapshot() = PlaybackSnapshot(
            currentPositionMs = positionMs,
            durationMs = 100_000L,
            liveOffsetMs = null,
            isSeekable = true,
            isPlaying = true,
        )

        override fun load(loadId: Long, source: StreamSource) {
            listener.onReady(loadId, true)
        }

        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) {
            this.positionMs = positionMs
        }
        override fun goLive() = Unit
        override fun stop() = Unit
        override fun release() = Unit
        override fun setListener(listener: PlayerPort.Listener) {
            this.listener = listener
        }
    }
}
