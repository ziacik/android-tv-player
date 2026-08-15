package sk.ziacik.androidtvplayer.player

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
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
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.StreamResolution
import sk.ziacik.androidtvplayer.resolver.StreamSource

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerTest {
    @Test
    fun `initial channel is retained from resolving through ready`() = runTest {
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.DVOJKA,
            resolve = { playableResolution() },
            playerPort = player,
        )

        assertEquals(
            PlayerUiState.Resolving(TvChannel.DVOJKA),
            controller.state.value,
        )

        controller.start()
        advanceUntilIdle()
        player.registeredListener.onReady(player.latestLoadId, isPlaying = true)

        val ready = controller.state.value as PlayerUiState.Ready
        assertEquals(TvChannel.DVOJKA, ready.channel)
    }

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
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { _ ->
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
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { throw IOException("offline") },
            playerPort = FakePlayerPort(),
        )

        controller.start()
        advanceUntilIdle()

        assertEquals(
            PlayerUiState.Error(
                TvChannel.JEDNOTKA,
                "Stream sa nepodarilo načítať",
            ),
            controller.state.value,
        )
    }

    @Test
    fun `resolve failure records its diagnostic cause`() = runTest {
        val failure = IOException("offline")
        val diagnostics = mutableListOf<Pair<String, Throwable?>>()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
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
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { playableResolution() },
            playerPort = player,
            diagnostics = { message, cause -> diagnostics += message to cause },
        )

        controller.start()
        advanceUntilIdle()
        player.registeredListener.onError(
            player.latestLoadId,
            "ERROR_CODE_IO_BAD_HTTP_STATUS",
        )

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
        player.registeredListener.onReady(player.latestLoadId, isPlaying = true)
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
        player.registeredListener.onReady(player.latestLoadId, isPlaying = false)

        player.snapshot = player.snapshot.copy(isPlaying = true)
        player.registeredListener.onPlayingChanged(player.latestLoadId, isPlaying = true)

        assertTrue((controller.state.value as PlayerUiState.Ready).playback.isPlaying)
    }

    @Test
    fun `restricted program retries two seconds after announced end`() = runTest {
        val resolvedChannels = mutableListOf<TvChannel>()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { channel ->
                resolvedChannels += channel
                if (resolvedChannels.size == 1) {
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
        assertEquals(listOf(TvChannel.JEDNOTKA), resolvedChannels)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(TvChannel.JEDNOTKA, TvChannel.JEDNOTKA), resolvedChannels)
    }

    @Test
    fun `missing end time uses one minute retry`() = runTest {
        var resolveCalls = 0
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { _ ->
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
        val resolvedChannels = mutableListOf<TvChannel>()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { channel ->
                resolvedChannels += channel
                if (resolvedChannels.size == 1) {
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

        assertEquals(listOf(TvChannel.JEDNOTKA, TvChannel.JEDNOTKA), resolvedChannels)
    }

    @Test
    fun `release cancels scheduled retry and releases player`() = runTest {
        var resolveCalls = 0
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { _ ->
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

    @Test
    fun `channel up stops old source loads Dvojka and persists it`() = runTest {
        val resolvedChannels = mutableListOf<TvChannel>()
        val savedChannels = mutableListOf<TvChannel>()
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { channel ->
                resolvedChannels += channel
                playableResolution(channel.storageKey)
            },
            playerPort = player,
            onChannelSelected = savedChannels::add,
        )

        controller.start()
        advanceUntilIdle()
        controller.channelUp()
        advanceUntilIdle()

        assertEquals(listOf(TvChannel.JEDNOTKA, TvChannel.DVOJKA), resolvedChannels)
        assertEquals(listOf(TvChannel.DVOJKA), savedChannels)
        assertEquals(1, player.stopCalls)
        assertEquals(TvChannel.DVOJKA, controller.state.value.channel)
        assertEquals("https://cdn.example/dvojka.m3u8", player.loadedSources.last().url)
    }

    @Test
    fun `channel down wraps from Jednotka to Dvojka`() = runTest {
        val controller = controller(FakePlayerPort(), this)

        controller.channelDown()
        advanceUntilIdle()

        assertEquals(TvChannel.DVOJKA, controller.state.value.channel)
    }

    @Test
    fun `stale cancelled resolve cannot replace latest channel`() = runTest {
        val jednotka = CompletableDeferred<StreamResolution>()
        val dvojka = CompletableDeferred<StreamResolution>()
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { channel ->
                if (channel == TvChannel.JEDNOTKA) jednotka.await() else dvojka.await()
            },
            playerPort = player,
        )

        controller.start()
        runCurrent()
        controller.channelUp()
        runCurrent()
        dvojka.complete(playableResolution("dvojka"))
        advanceUntilIdle()
        jednotka.complete(playableResolution("jednotka"))
        advanceUntilIdle()

        assertEquals(TvChannel.DVOJKA, controller.state.value.channel)
        assertEquals(
            listOf("https://cdn.example/dvojka.m3u8"),
            player.loadedSources.map(StreamSource::url),
        )
    }

    @Test
    fun `old player callbacks cannot overwrite a channel being resolved`() = runTest {
        val dvojka = CompletableDeferred<StreamResolution>()
        val diagnostics = mutableListOf<String>()
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { channel ->
                if (channel == TvChannel.JEDNOTKA) {
                    playableResolution("jednotka")
                } else {
                    dvojka.await()
                }
            },
            playerPort = player,
            diagnostics = { message, _ -> diagnostics += message },
        )

        controller.start()
        advanceUntilIdle()
        val oldLoadId = player.latestLoadId
        player.registeredListener.onReady(oldLoadId, isPlaying = true)
        controller.channelUp()
        runCurrent()
        player.registeredListener.onReady(oldLoadId, isPlaying = true)
        player.registeredListener.onError(oldLoadId, "OLD_SOURCE_ERROR")

        assertEquals(PlayerUiState.Resolving(TvChannel.DVOJKA), controller.state.value)
        assertTrue(diagnostics.isEmpty())
        dvojka.complete(playableResolution("dvojka"))
        advanceUntilIdle()
    }

    @Test
    fun `switching cancels restricted retry for old channel`() = runTest {
        val calls = mutableListOf<TvChannel>()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { channel ->
                calls += channel
                if (channel == TvChannel.JEDNOTKA) {
                    StreamResolution.Unavailable(PROGRAM.copy(endsAtMs = null))
                } else {
                    playableResolution("dvojka")
                }
            },
            playerPort = FakePlayerPort(),
        )

        controller.start()
        runCurrent()
        controller.channelUp()
        runCurrent()
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(listOf(TvChannel.JEDNOTKA, TvChannel.DVOJKA), calls)
    }

    @Test
    fun `callbacks from replaced load are ignored after Dvojka loads`() = runTest {
        val diagnostics = mutableListOf<String>()
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { channel -> playableResolution(channel.storageKey) },
            playerPort = player,
            diagnostics = { message, _ -> diagnostics += message },
        )

        controller.start()
        advanceUntilIdle()
        val jednotkaLoadId = player.latestLoadId
        controller.channelUp()
        advanceUntilIdle()
        val dvojkaLoadId = player.latestLoadId

        player.registeredListener.onReady(jednotkaLoadId, isPlaying = true)
        player.registeredListener.onError(jednotkaLoadId, "OLD_SOURCE_ERROR")

        assertEquals(PlayerUiState.Preparing(TvChannel.DVOJKA), controller.state.value)
        assertTrue(diagnostics.isEmpty())
        player.registeredListener.onReady(dvojkaLoadId, isPlaying = true)
        assertEquals(TvChannel.DVOJKA, controller.state.value.channel)
        assertTrue(controller.state.value is PlayerUiState.Ready)
    }

    @Test
    fun `callbacks from first Jednotka load are ignored after A B A switching`() = runTest {
        val diagnostics = mutableListOf<String>()
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = { channel -> playableResolution(channel.storageKey) },
            playerPort = player,
            diagnostics = { message, _ -> diagnostics += message },
        )

        controller.start()
        advanceUntilIdle()
        val firstJednotkaLoadId = player.latestLoadId
        controller.channelUp()
        advanceUntilIdle()
        controller.channelUp()
        advanceUntilIdle()
        val latestJednotkaLoadId = player.latestLoadId

        player.registeredListener.onReady(firstJednotkaLoadId, isPlaying = true)
        player.registeredListener.onError(firstJednotkaLoadId, "VERY_OLD_SOURCE_ERROR")

        assertEquals(PlayerUiState.Preparing(TvChannel.JEDNOTKA), controller.state.value)
        assertTrue(diagnostics.isEmpty())
        player.registeredListener.onReady(latestJednotkaLoadId, isPlaying = true)
        assertTrue(controller.state.value is PlayerUiState.Ready)
    }

    @Test
    fun `release is terminal and idempotent`() = runTest {
        var resolveCalls = 0
        val savedChannels = mutableListOf<TvChannel>()
        val diagnostics = mutableListOf<String>()
        val player = FakePlayerPort()
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = {
                resolveCalls += 1
                playableResolution()
            },
            playerPort = player,
            onChannelSelected = savedChannels::add,
            diagnostics = { message, _ -> diagnostics += message },
        )

        controller.start()
        advanceUntilIdle()
        val releasedLoadId = player.latestLoadId
        val stateAtRelease = controller.state.value
        controller.release()
        controller.release()

        controller.start()
        controller.retry()
        controller.channelUp()
        controller.channelDown()
        controller.refreshPlaybackSnapshot()
        controller.seekBack()
        controller.seekForward()
        controller.togglePlayback()
        controller.goLive()
        player.registeredListener.onReady(releasedLoadId, isPlaying = true)
        player.registeredListener.onPlayingChanged(releasedLoadId, isPlaying = false)
        player.registeredListener.onError(releasedLoadId, "AFTER_RELEASE")
        advanceUntilIdle()

        assertEquals(1, resolveCalls)
        assertEquals(1, player.releaseCalls)
        assertEquals(0, player.stopCalls)
        assertTrue(player.seekPositions.isEmpty())
        assertEquals(0, player.playCalls)
        assertEquals(0, player.pauseCalls)
        assertEquals(0, player.goLiveCalls)
        assertTrue(savedChannels.isEmpty())
        assertTrue(diagnostics.isEmpty())
        assertEquals(stateAtRelease, controller.state.value)
    }

    @Test
    fun `synchronous player load failure emits channel error and rejects callbacks`() = runTest {
        val failure = IllegalStateException("https://cdn.example/secret-token.m3u8")
        val diagnostics = mutableListOf<Pair<String, Throwable?>>()
        val player = FakePlayerPort(loadFailure = failure)
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.DVOJKA,
            resolve = { playableResolution("secret-token") },
            playerPort = player,
            diagnostics = { message, cause -> diagnostics += message to cause },
        )

        controller.start()
        advanceUntilIdle()
        val failedLoadId = player.latestLoadId

        assertEquals(
            PlayerUiState.Error(TvChannel.DVOJKA, "Stream sa nepodarilo načítať"),
            controller.state.value,
        )
        assertEquals("Media3 load failed", diagnostics.single().first)
        assertEquals(null, diagnostics.single().second)
        assertTrue(!diagnostics.single().first.contains("secret-token"))

        player.registeredListener.onReady(failedLoadId, isPlaying = true)
        player.registeredListener.onError(failedLoadId, "LATE_LOAD_CALLBACK")

        assertEquals(
            PlayerUiState.Error(TvChannel.DVOJKA, "Stream sa nepodarilo načítať"),
            controller.state.value,
        )
        assertEquals(1, diagnostics.size)
    }

    private fun controller(
        player: FakePlayerPort,
        scope: CoroutineScope,
        initialChannel: TvChannel = TvChannel.JEDNOTKA,
    ) = PlayerController(
        scope = scope,
        initialChannel = initialChannel,
        resolve = { playableResolution() },
        playerPort = player,
    )

    private fun playableResolution(suffix: String = "live") = StreamResolution.Playable(
        program = PROGRAM,
        source = StreamSource("https://cdn.example/$suffix.m3u8", "ua"),
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
        var loadFailure: Throwable? = null,
    ) : PlayerPort {
        lateinit var registeredListener: PlayerPort.Listener
        val loadedSources = mutableListOf<StreamSource>()
        val loadIds = mutableListOf<Long>()
        val seekPositions = mutableListOf<Long>()
        var goLiveCalls = 0
        var playCalls = 0
        var pauseCalls = 0
        var stopCalls = 0
        var releaseCalls = 0

        override fun snapshot() = snapshot

        val latestLoadId: Long
            get() = loadIds.last()

        override fun load(loadId: Long, source: StreamSource) {
            loadIds += loadId
            loadedSources += source
            loadFailure?.let { throw it }
        }

        override fun play() {
            playCalls += 1
            snapshot = snapshot.copy(isPlaying = true)
        }

        override fun pause() {
            pauseCalls += 1
            snapshot = snapshot.copy(isPlaying = false)
        }

        override fun seekTo(positionMs: Long) {
            seekPositions += positionMs
        }

        override fun goLive() {
            goLiveCalls += 1
        }

        override fun stop() {
            stopCalls += 1
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
