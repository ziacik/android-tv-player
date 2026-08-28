package sk.ziacik.androidtvplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.ChannelProvider
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

class MiniEpgModelTest {
    private val channels = (1..7).map { number ->
        TvChannel(
            storageKey = "channel-$number",
            displayName = "CHANNEL $number",
            provider = ChannelProvider.DIRECT,
        )
    }

    @Test
    fun `five rows are centered on selected channel`() {
        val selected = channels[3]
        val rows = buildMiniEpgRows(
            channels = channels,
            currentChannel = channels[2],
            selectedChannel = selected,
            programmes = emptyMap(),
            nowMs = 5_000L,
        )

        assertEquals(listOf(2, 3, 4, 5, 6), rows.map { it.channelNumber })
        assertEquals(selected.storageKey, rows[2].channel.storageKey)
        assertTrue(rows[2].isSelected)
        assertTrue(rows[1].isCurrent)
    }

    @Test
    fun `window wraps around the ends of the channel list`() {
        val rows = buildMiniEpgRows(
            channels = channels,
            currentChannel = channels.first(),
            selectedChannel = channels.first(),
            programmes = emptyMap(),
            nowMs = 5_000L,
        )

        assertEquals(listOf(6, 7, 1, 2, 3), rows.map { it.channelNumber })
        assertTrue(rows[2].isSelected)
    }

    @Test
    fun `programme progress is calculated and clamped`() {
        val programme = ProgramMetadata(
            title = "Current show",
            startsAtMs = 1_000L,
            endsAtMs = 9_000L,
        )
        val rows = buildMiniEpgRows(
            channels = channels,
            currentChannel = channels[3],
            selectedChannel = channels[3],
            programmes = mapOf(channels[3].storageKey to programme),
            nowMs = 5_000L,
        )

        val selected = rows.single { it.isSelected }
        assertEquals("Current show", selected.programmeTitle)
        assertEquals(0.5f, selected.progress ?: -1f, 0.001f)
        assertFalse(selected.programmeTitle.isBlank())
    }
}
