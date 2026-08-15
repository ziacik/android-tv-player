package sk.ziacik.androidtvplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.player.PlaybackSnapshot
import sk.ziacik.androidtvplayer.player.PlayerUiState
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

class PlayerOverlayModelTest {
    private val program = ProgramMetadata(
        title = "Večerný program",
        startsAtMs = null,
        endsAtMs = null,
        internetAllowed = true,
    )

    @Test
    fun `uses real progress and live offset`() {
        val model = PlayerOverlayModel.from(
            ready(position = 40_000L, duration = 100_000L, offset = 60_000L),
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
        )

        assertTrue(model.isLive)
        assertNull(model.delayText)
        assertEquals("NAŽIVO", model.liveActionText)
    }

    @Test
    fun `falls back to duration minus position`() {
        val model = PlayerOverlayModel.from(
            ready(position = 3_661_000L, duration = 7_200_000L, offset = null),
        )

        assertEquals("−58:59", model.delayText)
    }

    @Test
    fun `progress clamps to live window bounds`() {
        assertEquals(
            0f,
            PlayerOverlayModel.from(
                ready(position = -10_000L, duration = 100_000L, offset = 110_000L),
            ).progress,
        )
        assertEquals(
            1f,
            PlayerOverlayModel.from(
                ready(position = 120_000L, duration = 100_000L, offset = 0L),
            ).progress,
        )
    }

    @Test
    fun `non-seekable item has inactive timeline`() {
        val model = PlayerOverlayModel.from(
            ready(
                position = 0L,
                duration = null,
                offset = null,
                seekable = false,
            ),
        )

        assertNull(model.progress)
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
        )

        assertEquals("−1:01:01", model.delayText)
    }

    private fun ready(
        position: Long,
        duration: Long?,
        offset: Long?,
        seekable: Boolean = true,
    ) = PlayerUiState.Ready(
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
