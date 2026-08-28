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

        assertEquals("2 · DVOJKA", model.channelLabel)
    }

    @Test
    fun `uses watched programme progress and exposes its EPG times`() {
        val model = PlayerOverlayModel.from(
            ready(position = 40_000L, duration = 100_000L, offset = 60_000L),
            nowMs = 100_000L,
        )

        assertEquals(0.4f, model.progress!!, 0.0001f)
        assertEquals(0L, model.programmeStartMs)
        assertEquals(40_000L, model.programmeNowMs)
        assertEquals(100_000L, model.programmeEndMs)
        assertFalse(model.isLive)
        assertEquals("NAŽIVO", model.liveActionText)
    }

    @Test
    fun `ten seconds or less counts as live`() {
        val model = PlayerOverlayModel.from(
            ready(position = 97_000L, duration = 100_000L, offset = 3_000L),
            nowMs = 40_000L,
        )

        assertTrue(model.isLive)
        assertEquals("NAŽIVO", model.liveActionText)
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
    fun `future programme starts its timeline at the current time`() {
        val model = PlayerOverlayModel.from(
            ready(position = 0L, duration = null, offset = null).copy(
                program = ProgramMetadata("Nasledujúci program", 100_000L, 160_000L, true),
            ),
            nowMs = 40_000L,
        )

        assertEquals(0f, model.progress!!, 0.0001f)
        assertEquals(40_000L, model.programmeStartMs)
        assertEquals(40_000L, model.programmeNowMs)
        assertEquals(160_000L, model.programmeEndMs)
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
        assertEquals(0L, model.programmeStartMs)
        assertFalse(model.isSeekable)
    }

    @Test
    fun `programme timeline follows the stream window position`() {
        val model = PlayerOverlayModel.from(
            ready(position = 95_000L, duration = 100_000L, offset = 5_000L).copy(
                program = ProgramMetadata("Film", 1_000L, 101_000L, true),
            ),
            nowMs = 41_000L,
        )

        assertEquals(0.35f, model.progress!!, 0.0001f)
        assertEquals(36_000L, model.programmeNowMs)
        assertTrue(model.isLive)
    }

    @Test
    fun `missing programme timestamps fall back to seekable stream progress`() {
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

        assertEquals(0.4f, missing.progress!!, 0.0001f)
        assertEquals(0.4f, reversed.progress!!, 0.0001f)
        assertNull(missing.programmeStartMs)
        assertNull(missing.programmeNowMs)
        assertNull(missing.programmeEndMs)
        assertNull(reversed.programmeStartMs)
        assertNull(reversed.programmeNowMs)
        assertNull(reversed.programmeEndMs)
    }

    @Test
    fun `no EPG still exposes seekable stream progress`() {
        val model = PlayerOverlayModel.from(
            ready(position = 75_000L, duration = 100_000L, offset = 25_000L).copy(program = null),
            nowMs = 100_000L,
        )

        assertEquals(0.75f, model.progress!!, 0.0001f)
        assertNull(model.programmeStartMs)
        assertNull(model.programmeNowMs)
        assertNull(model.programmeEndMs)
        assertTrue(model.isSeekable)
    }

    @Test
    fun `switching model shows target programme and status`() {
        val model = PlayerOverlayModel.from(
            channel = TvChannel.DVOJKA,
            program = program,
            playback = null,
            statusText = "Prepínam…",
            nowMs = 40_000L,
        )

        assertEquals("2 · DVOJKA", model.channelLabel)
        assertEquals("Večerný program", model.programTitle)
        assertEquals(PlayerOverlayStateIndicator.SWITCHING, model.stateIndicator)
        assertEquals(40_000L, model.displayNowMs)
        assertEquals(0.4f, model.progress!!, 0.0001f)
        assertFalse(model.isPlaying)
        assertFalse(model.isSeekable)
    }

    @Test
    fun `recovery model without programme keeps an empty timeline slot`() {
        val model = PlayerOverlayModel.from(
            channel = TvChannel.DVOJKA,
            program = null,
            playback = null,
            statusText = "Obnovím o 12:54",
            nowMs = 40_000L,
        )

        assertEquals("2 · DVOJKA", model.channelLabel)
        assertEquals("", model.programTitle)
        assertEquals(PlayerOverlayStateIndicator.RETRYING, model.stateIndicator)
        assertEquals(40_000L, model.displayNowMs)
        assertNull(model.progress)
        assertNull(model.programmeStartMs)
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
