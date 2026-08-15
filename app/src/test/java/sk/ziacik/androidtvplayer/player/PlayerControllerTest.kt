package sk.ziacik.androidtvplayer.player

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.StreamResolution
import sk.ziacik.androidtvplayer.resolver.StreamSource

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerTest {
    @Test
    fun `seek back is exactly ten seconds and clamps to zero`() = runTest {
        val player = FakePlayerPort(
            snapshot = playback(currentPositionMs = 5_000L),
        )
        val controller = controller(player, this)

        controller.seekBack()

        assertEquals(listOf(0L), player.seekPositions)
    }

    @Test
    fun `seek forward is exactly ten seconds and clamps to duration`() = runTest {
        val player = FakePlayerPort(
            snapshot = playback(
                currentPositionMs = 95_000L,
                durationMs = 100_000L,
            ),
        )
        val controller = controller(player, this)

        controller.seekForward()

        assertEquals(listOf(100_000L), player.seekPositions)
    }

    @Test
    fun `seek does nothing when item is not seekable`() = runTest {
        val player = FakePlayerPort(snapshot = playback(isSeekable = false))
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
                StreamResolution.Playable(
                    program = PROGRAM,
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
    fun `resolve failure records its diagnostic cause`() = runTest {
        val failure = IOException("offline")
        val diagnostics = mutableListOf<Pair<String, Throwable?>>()
        val controller = PlayerController(
            scope = this,
            resolve = { throw failure },
            playerPort = FakePlayerPort(),
            diagnostics = { message, cause -> diagnostics += message to cause },
        )

        controller.start()
        advanceUntilIdle()

        assertEquals("STVR resolve failed", diagnostics.single().first)
        assertSame(failure, diagnostics.single().second)
    }

    @Test
    fun `player failure records Media3 diagnostic without a stream URL`() = runTest {
        val diagnostics = mutableListOf<Pair<String, Throwable?>>()
        val player = FakePlayerPort()
        PlayerController(
            scope = this,
            resolve = { playableResolution() },
            playerPort = player,
            diagnostics = { message, cause -> diagnostics += message to cause },
        )

        player.registeredListener.onError("ERROR_CODE_IO_BAD_HTTP_STATUS")

        assertEquals(
            "Media3 playback failed: ERROR_CODE_IO_BAD_HTTP_STATUS",
            diagnostics.single().first,
        )
        assertEquals(null, diagnostics.single().second)
    }

    @Test
    fun `ready state contains program and latest playback snapshot`() = runTest {
        val player = FakePlayerPort(
            snapshot = playback(
                currentPositionMs = 40_000L,
                durationMs = 100_000L,
                liveOffsetMs = 60_000L,
                isSeekable = true,
                isPlaying = true,
            ),
        )
        val controller = controller(player, this)

        controller.start()
        advanceUntilIdle()
        player.registeredListener.onReady(isPlaying = true)
        controller.refreshPlaybackSnapshot()

        assertEquals(PROGRAM, (controller.state.value as PlayerUiState.Ready).program)
        assertEquals(player.snapshot, (controller.state.value as PlayerUiState.Ready).playback)
    }

    @Test
    fun `playing callback refreshes ready playback state`() = runTest {
        val player = FakePlayerPort(snapshot = playback(isPlaying = false))
        val controller = controller(player, this)
        controller.start()
        advanceUntilIdle()
        player.registeredListener.onReady(isPlaying = false)

        player.snapshot = player.snapshot.copy(isPlaying = true)
        player.registeredListener.onPlayingChanged(isPlaying = true)

        assertTrue((controller.state.value as PlayerUiState.Ready).playback.isPlaying)
    }

    @Test
    fun `restricted program retries two seconds after announced end`() = runTest {
        var resolveCalls = 0
        val controller = PlayerController(
            scope = this,
            resolve = {
                resolveCalls += 1
                if (resolveCalls == 1) {
                    StreamResolution.Unavailable(PROGRAM.copy(endsAtMs = 20_000L))
                } else {
                    playableResolution()
                }
            },
            playerPort = FakePlayerPort(),
            nowMs = { 10_000L },
        )

        controller.start()
        runCurrent()
        assertTrue(controller.state.value is PlayerUiState.Unavailable)
        advanceTimeBy(11_999L)
        runCurrent()
        assertEquals(1, resolveCalls)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, resolveCalls)
    }

    @Test
    fun `missing end time uses one minute retry`() = runTest {
        var resolveCalls = 0
        val controller = PlayerController(
            scope = this,
            resolve = {
                resolveCalls += 1
                StreamResolution.Unavailable(PROGRAM.copy(endsAtMs = null))
            },
            playerPort = FakePlayerPort(),
            nowMs = { 10_000L },
        )

        controller.start()
        runCurrent()
        advanceTimeBy(59_999L)
        runCurrent()
        assertEquals(1, resolveCalls)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, resolveCalls)
        controller.release()
    }

    @Test
    fun `manual retry cancels scheduled restricted retry`() = runTest {
        var resolveCalls = 0
        val controller = PlayerController(
            scope = this,
            resolve = {
                resolveCalls += 1
                if (resolveCalls == 1) {
                    StreamResolution.Unavailable(PROGRAM.copy(endsAtMs = 70_000L))
                } else {
                    playableResolution()
                }
            },
            playerPort = FakePlayerPort(),
            nowMs = { 10_000L },
        )

        controller.start()
        runCurrent()
        controller.retry()
        runCurrent()
        advanceTimeBy(62_000L)
        runCurrent()

        assertEquals(2, resolveCalls)
    }

    @Test
    fun `release cancels scheduled retry and releases player`() = runTest {
        var resolveCalls = 0
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            resolve = {
                resolveCalls += 1
                StreamResolution.Unavailable(PROGRAM.copy(endsAtMs = null))
            },
            playerPort = player,
        )
        controller.start()
        runCurrent()

        controller.release()
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(1, resolveCalls)
        assertEquals(1, player.releaseCalls)
    }

    private fun controller(
        player: FakePlayerPort,
        scope: CoroutineScope,
    ) = PlayerController(
        scope = scope,
        resolve = { playableResolution() },
        playerPort = player,
    )

    private fun playableResolution() = StreamResolution.Playable(
        program = PROGRAM,
        source = StreamSource("https://cdn.example/live.m3u8", "ua"),
    )

    private fun playback(
        currentPositionMs: Long = 30_000L,
        durationMs: Long? = 100_000L,
        liveOffsetMs: Long? = 70_000L,
        isSeekable: Boolean = true,
        isPlaying: Boolean = true,
    ) = PlaybackSnapshot(
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        liveOffsetMs = liveOffsetMs,
        isSeekable = isSeekable,
        isPlaying = isPlaying,
    )

    private class FakePlayerPort(
        var snapshot: PlaybackSnapshot = PlaybackSnapshot(
            currentPositionMs = 30_000L,
            durationMs = 100_000L,
            liveOffsetMs = 70_000L,
            isSeekable = true,
            isPlaying = true,
        ),
    ) : PlayerPort {
        lateinit var registeredListener: PlayerPort.Listener
        val loadedSources = mutableListOf<StreamSource>()
        val seekPositions = mutableListOf<Long>()
        var goLiveCalls = 0
        var releaseCalls = 0

        override fun snapshot() = snapshot

        override fun load(source: StreamSource) {
            loadedSources += source
        }

        override fun play() {
            snapshot = snapshot.copy(isPlaying = true)
        }

        override fun pause() {
            snapshot = snapshot.copy(isPlaying = false)
        }

        override fun seekTo(positionMs: Long) {
            seekPositions += positionMs
        }

        override fun goLive() {
            goLiveCalls += 1
        }

        override fun release() {
            releaseCalls += 1
        }

        override fun setListener(listener: PlayerPort.Listener) {
            registeredListener = listener
        }
    }

    private companion object {
        val PROGRAM = ProgramMetadata(
            title = "Večerný program",
            startsAtMs = 1_000L,
            endsAtMs = 100_000L,
            internetAllowed = true,
        )
    }
}
