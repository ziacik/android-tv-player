package sk.ziacik.androidtvplayer.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.StreamResolution
import sk.ziacik.androidtvplayer.resolver.StreamSource

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerLifecycleTest {
    @Test
    fun `background stop blocks playback until a fresh start`() = runTest {
        var resolveCalls = 0
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = {
                resolveCalls += 1
                StreamResolution.Playable(
                    program = ProgramMetadata(
                        title = "Live",
                        startsAtMs = 1_000L,
                        endsAtMs = 100_000L,
                        internetAllowed = true,
                    ),
                    source = StreamSource(
                        url = "https://cdn.example/$resolveCalls.m3u8",
                        userAgent = "ua",
                    ),
                )
            },
            playerPort = player,
        )

        controller.start()
        advanceUntilIdle()
        assertEquals(1, resolveCalls)

        val stopMethod = runCatching {
            PlayerController::class.java.getDeclaredMethod("stop")
        }.getOrNull()
        assertTrue("PlayerController must expose a non-terminal stop()", stopMethod != null)

        stopMethod!!.invoke(controller)
        assertEquals(1, player.stopCalls)
        assertEquals(0, player.releaseCalls)

        controller.retry()
        advanceUntilIdle()
        assertEquals(1, resolveCalls)
        assertEquals(1, player.loadedSources.size)

        controller.start()
        advanceUntilIdle()

        assertEquals(2, resolveCalls)
        assertEquals(2, player.loadedSources.size)
        assertEquals(0, player.releaseCalls)
    }

    private class FakePlayerPort : PlayerPort {
        val loadedSources = mutableListOf<StreamSource>()
        var stopCalls = 0
        var releaseCalls = 0
        private var listener: PlayerPort.Listener? = null

        override fun snapshot() = PlaybackSnapshot(
            currentPositionMs = 0L,
            durationMs = null,
            liveOffsetMs = null,
            isSeekable = false,
            isPlaying = false,
        )

        override fun load(loadId: Long, source: StreamSource) {
            loadedSources += source
        }

        override fun play() = Unit

        override fun pause() = Unit

        override fun seekTo(positionMs: Long) = Unit

        override fun goLive() = Unit

        override fun stop() {
            stopCalls += 1
        }

        override fun release() {
            releaseCalls += 1
        }

        override fun setListener(listener: PlayerPort.Listener) {
            this.listener = listener
        }
    }
}
