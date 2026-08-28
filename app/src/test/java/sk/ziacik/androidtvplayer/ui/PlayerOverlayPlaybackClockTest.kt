package sk.ziacik.androidtvplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.player.PlaybackSnapshot
import sk.ziacik.androidtvplayer.player.PlayerUiState
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

class PlayerOverlayPlaybackClockTest {
    @Test
    fun `timeline follows watched position instead of wall clock when timeshifted`() {
        val model = PlayerOverlayModel.from(
            state = PlayerUiState.Ready(
                channel = TvChannel.JEDNOTKA,
                program = ProgramMetadata(
                    title = "Program",
                    startsAtMs = 900_000L,
                    endsAtMs = 1_100_000L,
                    internetAllowed = true,
                ),
                playback = PlaybackSnapshot(
                    currentPositionMs = 60_000L,
                    durationMs = 100_000L,
                    liveOffsetMs = 40_000L,
                    isSeekable = true,
                    isPlaying = true,
                ),
            ),
            nowMs = 1_000_000L,
        )

        assertEquals(960_000L, model.programmeNowMs)
        assertEquals(0.3f, model.progress!!, 0.0001f)
        assertEquals(1_000_000L, model.displayNowMs)
    }
}
