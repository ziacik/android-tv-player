package sk.ziacik.androidtvplayer.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.epg.EpgRepository
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.StreamResolution
import sk.ziacik.androidtvplayer.resolver.StreamSource

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerEpgFallbackTest {
    @Test
    fun `fallback title stays empty while EPG loads then becomes channel name when missing`() = runTest {
        val epgResult = CompletableDeferred<ProgramMetadata?>()
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = {
                StreamResolution.Playable(
                    program = ProgramMetadata(
                        title = TvChannel.JEDNOTKA.displayName,
                        startsAtMs = null,
                        endsAtMs = null,
                        internetAllowed = true,
                    ),
                    source = StreamSource("https://cdn.example/live.m3u8", "ua"),
                )
            },
            playerPort = player,
            epgRepository = EpgRepository { _, _ -> epgResult.await() },
        )

        controller.start()
        advanceUntilIdle()
        player.listener.onReady(player.latestLoadId, isPlaying = true)
        runCurrent()

        assertEquals("", (controller.state.value as PlayerUiState.Ready).program.title)

        epgResult.complete(null)
        advanceUntilIdle()

        assertEquals(
            TvChannel.JEDNOTKA.displayName,
            (controller.state.value as PlayerUiState.Ready).program.title,
        )
    }

    private class FakePlayerPort : PlayerPort {
        lateinit var listener: PlayerPort.Listener
        private val loadIds = mutableListOf<Long>()

        val latestLoadId: Long
            get() = loadIds.last()

        override fun snapshot() = PlaybackSnapshot(
            currentPositionMs = 0L,
            durationMs = null,
            liveOffsetMs = 0L,
            isSeekable = false,
            isPlaying = true,
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
