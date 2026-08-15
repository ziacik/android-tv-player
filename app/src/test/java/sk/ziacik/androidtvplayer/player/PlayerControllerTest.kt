package sk.ziacik.androidtvplayer.player

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.resolver.StreamSource

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerTest {
    @Test
    fun `seek back is exactly ten seconds and clamps to zero`() = runTest {
        val player = FakePlayerPort(currentPositionMs = 5_000L)
        val controller = controller(player, this)

        controller.seekBack()

        assertEquals(listOf(0L), player.seekPositions)
    }

    @Test
    fun `seek forward is exactly ten seconds and clamps to duration`() = runTest {
        val player = FakePlayerPort(
            currentPositionMs = 95_000L,
            durationMs = 100_000L,
        )
        val controller = controller(player, this)

        controller.seekForward()

        assertEquals(listOf(100_000L), player.seekPositions)
    }

    @Test
    fun `seek does nothing when item is not seekable`() = runTest {
        val player = FakePlayerPort(isSeekable = false)
        val controller = controller(player, this)

        controller.seekBack()
        controller.seekForward()

        assertTrue(player.seekPositions.isEmpty())
    }

    @Test
    fun `go live delegates to default live position`() = runTest {
        val player = FakePlayerPort()
        val controller = controller(player, this)

        controller.goLive()

        assertEquals(1, player.goLiveCalls)
    }

    @Test
    fun `retry performs a fresh resolve and reload`() = runTest {
        var resolveCalls = 0
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            resolve = {
                resolveCalls += 1
                StreamSource(
                    url = "https://cdn.example/$resolveCalls.m3u8",
                    userAgent = "ua",
                )
            },
            playerPort = player,
        )

        controller.start()
        advanceUntilIdle()
        controller.retry()
        advanceUntilIdle()

        assertEquals(2, resolveCalls)
        assertEquals(
            listOf(
                "https://cdn.example/1.m3u8",
                "https://cdn.example/2.m3u8",
            ),
            player.loadedSources.map(StreamSource::url),
        )
    }

    @Test
    fun `resolve failure exposes error state`() = runTest {
        val controller = PlayerController(
            scope = this,
            resolve = { throw IOException("offline") },
            playerPort = FakePlayerPort(),
        )

        controller.start()
        advanceUntilIdle()

        assertEquals(
            PlayerUiState.Error("Stream sa nepodarilo načítať"),
            controller.state.value,
        )
    }

    @Test
    fun `ready callback exposes seekability and playback`() = runTest {
        val player = FakePlayerPort(isSeekable = true)
        val controller = controller(player, this)

        player.registeredListener.onReady(isPlaying = true)

        assertEquals(
            PlayerUiState.Ready(isPlaying = true, isSeekable = true),
            controller.state.value,
        )
    }

    private fun controller(
        player: FakePlayerPort,
        scope: CoroutineScope,
    ) = PlayerController(
        scope = scope,
        resolve = { StreamSource("https://cdn.example/live.m3u8", "ua") },
        playerPort = player,
    )

    private class FakePlayerPort(
        override var currentPositionMs: Long = 30_000L,
        override var durationMs: Long? = 100_000L,
        override var isSeekable: Boolean = true,
        override var isPlaying: Boolean = true,
    ) : PlayerPort {
        lateinit var registeredListener: PlayerPort.Listener
        val loadedSources = mutableListOf<StreamSource>()
        val seekPositions = mutableListOf<Long>()
        var goLiveCalls = 0

        override fun load(source: StreamSource) {
            loadedSources += source
        }

        override fun play() {
            isPlaying = true
        }

        override fun pause() {
            isPlaying = false
        }

        override fun seekTo(positionMs: Long) {
            seekPositions += positionMs
        }

        override fun goLive() {
            goLiveCalls += 1
        }

        override fun release() = Unit

        override fun setListener(listener: PlayerPort.Listener) {
            registeredListener = listener
        }
    }
}
