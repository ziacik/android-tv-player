package sk.ziacik.androidtvplayer.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata
import sk.ziacik.androidtvplayer.resolver.StreamResolution
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

    private class FakePlayerPort : PlayerPort {
        val loadedSources = mutableListOf<StreamSource>()
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
