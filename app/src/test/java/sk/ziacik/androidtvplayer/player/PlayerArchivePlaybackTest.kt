package sk.ziacik.androidtvplayer.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.StreamResolution
import sk.ziacik.androidtvplayer.resolver.StreamResolveException
import sk.ziacik.androidtvplayer.resolver.StreamSource

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerArchivePlaybackTest {
    @Test
    fun `archive playback stays on archive after its EPG end and go live resolves live again`() = runTest {
        var liveResolveCalls = 0
        val player = FakePlayerPort()
        val archiveProgramme = ProgramMetadata(
            title = "Ranné správy",
            startsAtMs = 10_000L,
            endsAtMs = 20_000L,
            internetAllowed = true,
        )
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = {
                liveResolveCalls += 1
                StreamResolution.Playable(
                    program = ProgramMetadata(
                        title = "Live",
                        startsAtMs = 90_000L,
                        endsAtMs = 120_000L,
                        internetAllowed = true,
                    ),
                    source = StreamSource(
                        url = "https://cdn.example/live-$liveResolveCalls.m3u8",
                        userAgent = "ua",
                    ),
                )
            },
            resolveArchive = { _, programme ->
                assertEquals(archiveProgramme, programme)
                StreamSource(
                    url = "https://cdn.example/archive.m3u8",
                    userAgent = "ua",
                )
            },
            playerPort = player,
            nowMs = { 100_000L },
        )

        controller.start()
        advanceUntilIdle()
        assertEquals("https://cdn.example/live-1.m3u8", player.loadedSources.last().url)

        controller.playArchive(TvChannel.JEDNOTKA, archiveProgramme)
        advanceUntilIdle()
        assertEquals("https://cdn.example/archive.m3u8", player.loadedSources.last().url)

        controller.refreshPlaybackSnapshot()
        advanceUntilIdle()
        assertEquals(1, liveResolveCalls)
        assertEquals("https://cdn.example/archive.m3u8", player.loadedSources.last().url)

        controller.goLive()
        advanceUntilIdle()
        assertEquals(2, liveResolveCalls)
        assertEquals("https://cdn.example/live-2.m3u8", player.loadedSources.last().url)
    }

    @Test
    fun `archive resolve failure keeps live playing and shows a transient notice`() = runTest {
        val player = FakePlayerPort()
        val liveProgramme = ProgramMetadata(
            title = "Live relácia",
            startsAtMs = 90_000L,
            endsAtMs = 120_000L,
            internetAllowed = true,
        )
        val archiveProgramme = ProgramMetadata(
            title = "Duel",
            startsAtMs = 10_000L,
            endsAtMs = 20_000L,
            internetAllowed = true,
        )
        val controller = PlayerController(
            scope = this,
            initialChannel = TvChannel.JEDNOTKA,
            resolve = {
                StreamResolution.Playable(
                    program = liveProgramme,
                    source = StreamSource(
                        url = "https://cdn.example/live.m3u8",
                        userAgent = "ua",
                    ),
                )
            },
            resolveArchive = { _, _ ->
                throw StreamResolveException("STVR archive item was not found")
            },
            playerPort = player,
            nowMs = { 100_000L },
        )

        controller.start()
        advanceUntilIdle()

        controller.playArchive(TvChannel.JEDNOTKA, archiveProgramme)
        runCurrent()

        assertTrue(controller.state.value is PlayerUiState.Ready)
        val state = controller.state.value as PlayerUiState.Ready
        assertEquals(liveProgramme, state.program)
        assertEquals("Program nie je dostupný v archíve", state.noticeText)
        assertEquals(0, player.pauseCalls)
        assertEquals(0, player.stopCalls)
        assertEquals(listOf("https://cdn.example/live.m3u8"), player.loadedSources.map { it.url })

        advanceTimeBy(4_000L)
        runCurrent()
        assertNull((controller.state.value as PlayerUiState.Ready).noticeText)
    }

    private class FakePlayerPort : PlayerPort {
        val loadedSources = mutableListOf<StreamSource>()
        var pauseCalls = 0
        var stopCalls = 0
        private lateinit var listener: PlayerPort.Listener

        override fun snapshot() = PlaybackSnapshot(
            currentPositionMs = 0L,
            durationMs = 10_000L,
            liveOffsetMs = null,
            isSeekable = true,
            isPlaying = true,
        )

        override fun load(loadId: Long, source: StreamSource) {
            loadedSources += source
            listener.onReady(loadId, true)
        }

        override fun play() = Unit
        override fun pause() {
            pauseCalls += 1
        }
        override fun seekTo(positionMs: Long) = Unit
        override fun goLive() = Unit
        override fun stop() {
            stopCalls += 1
        }
        override fun release() = Unit
        override fun setListener(listener: PlayerPort.Listener) {
            this.listener = listener
        }
    }
}
