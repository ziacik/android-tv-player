package sk.ziacik.androidtvplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.player.PlaybackSnapshot
import sk.ziacik.androidtvplayer.player.PlayerUiState
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

class PlayerOverlayModelTest {
    private val program = ProgramMetadata(
        title = "Večerný program",
        startsAtMs = 0L,
        endsAtMs = 100_000L,
        internetAllowed = true,
    )

    @Test
    fun `ready Dvojka uses its channel in the live label`() {
        val model = PlayerOverlayModel.from(
            PlayerUiState.Ready(
                channel = TvChannel.DVOJKA,
                program = program,
                playback = PlaybackSnapshot(
                    currentPositionMs = 100_000L,
                    durationMs = 100_000L,
                    liveOffsetMs = 0L,
                    isSeekable = true,
                    isPlaying = true,
                ),
            ),
            nowMs = 40_000L,
        )

        assertEquals("DVOJKA · NAŽIVO", model.channelLabel)
    }

    @Test
    fun `uses programme progress and live offset`() {
        val model = PlayerOverlayModel.from(
            ready(position = 40_000L, duration = 100_000L, offset = 60_000L),
            nowMs = 40_000L,
        )

        assertEquals(0.4f, model.progress!!, 0.0001f)
        assertEquals("−1:00", model.delayText)
        assertFalse(model.isLive)
        assertEquals("NA LIVE", model.liveActionText)
    }

    @Test
    fun `ten seconds or less counts as live`() {
        val model = PlayerOverlayModel.from(
            ready(position = 97_000L, duration = 100_000L, offset = 3_000L),
            nowMs = 40_000L,
        )

        assertTrue(model.isLive)
        assertNull(model.delayText)
        assertEquals("NAŽIVO", model.liveActionText)
    }

    @Test
    fun `falls back to duration minus position`() {
        val model = PlayerOverlayModel.from(
            ready(position = 3_661_000L, duration = 7_200_000L, offset = null),
            nowMs = 40_000L,
        )

        assertEquals("−58:59", model.delayText)
    }

    @Test
    fun `progress clamps to programme bounds`() {
        assertEquals(
            0f,
            PlayerOverlayModel.from(
                ready(position = -10_000L, duration = 100_000L, offset = 110_000L),
                nowMs = -1L,
            ).progress,
        )
        assertEquals(
            1f,
            PlayerOverlayModel.from(
                ready(position = 120_000L, duration = 100_000L, offset = 0L),
                nowMs = 100_001L,
            ).progress,
        )
    }

    @Test
    fun `programme timeline stays active for a non-seekable stream`() {
        val model = PlayerOverlayModel.from(
            ready(
                position = 0L,
                duration = null,
                offset = null,
                seekable = false,
            ),
            nowMs = 40_000L,
        )

        assertEquals(0.4f, model.progress!!, 0.0001f)
        assertNull(model.delayText)
        assertFalse(model.isSeekable)
    }

    @Test
    fun `hour delay uses hour minute second format`() {
        val model = PlayerOverlayModel.from(
            ready(
                position = 1_000L,
                duration = 3_662_000L,
                offset = 3_661_000L,
            ),
            nowMs = 40_000L,
        )

        assertEquals("−1:01:01", model.delayText)
    }

    @Test
    fun `programme timeline ignores the stream window position`() {
        val model = PlayerOverlayModel.from(
            ready(position = 95_000L, duration = 100_000L, offset = 5_000L).copy(
                program = ProgramMetadata("Film", 1_000L, 101_000L, true),
            ),
            nowMs = 41_000L,
        )

        assertEquals(0.4f, model.progress!!, 0.0001f)
        assertTrue(model.isLive)
    }

    @Test
    fun `missing or reversed programme timestamps hide the progress marker`() {
        val missing = PlayerOverlayModel.from(
            ready(position = 40_000L, duration = 100_000L, offset = 60_000L).copy(
                program = program.copy(startsAtMs = null),
            ),
            nowMs = 40_000L,
        )
        val reversed = PlayerOverlayModel.from(
            ready(position = 40_000L, duration = 100_000L, offset = 60_000L).copy(
                program = program.copy(startsAtMs = 100_000L, endsAtMs = 1_000L),
            ),
            nowMs = 40_000L,
        )

        assertNull(missing.progress)
        assertNull(reversed.progress)
        assertEquals("−1:00", missing.delayText)
    }

    private fun ready(
        position: Long,
        duration: Long?,
        offset: Long?,
        seekable: Boolean = true,
    ) = PlayerUiState.Ready(
        channel = TvChannel.JEDNOTKA,
        program = program,
        playback = PlaybackSnapshot(
            currentPositionMs = position,
            durationMs = duration,
            liveOffsetMs = offset,
            isSeekable = seekable,
            isPlaying = true,
        ),
    )
}
