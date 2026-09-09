package sk.ziacik.androidtvplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.ArchiveConfig
import sk.ziacik.androidtvplayer.channel.ArchiveProvider
import sk.ziacik.androidtvplayer.channel.ChannelProvider
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

class MiniEpgProgrammeNavigationTest {
    private val programme = ProgramMetadata(
        title = "Program",
        startsAtMs = 10_000L,
        endsAtMs = 20_000L,
        internetAllowed = true,
    )
    private val archiveChannel = TvChannel(
        storageKey = "jednotka",
        displayName = "JEDNOTKA",
        provider = ChannelProvider.DIRECT,
        providerValue = "https://example.com/live.m3u8",
        archive = ArchiveConfig(ArchiveProvider.STVR, channelId = "1"),
    )

    @Test
    fun `previous programme lookup samples just before selected programme`() {
        assertEquals(9_999L, previousProgrammeLookupTime(programme))
    }

    @Test
    fun `next programme lookup samples the selected programme end`() {
        assertEquals(20_000L, nextProgrammeLookupTime(programme))
    }

    @Test
    fun `programme counts as archive only after it has ended`() {
        assertFalse(isPastProgramme(programme, 19_999L))
        assertTrue(isPastProgramme(programme, 20_000L))
    }

    @Test
    fun `past archive programme is loading until availability is known`() {
        assertEquals(
            MiniEpgArchiveState.LOADING,
            miniEpgArchiveState(
                channel = archiveChannel,
                programme = programme,
                nowMs = 20_000L,
                available = null,
            ),
        )
    }

    @Test
    fun `archive availability distinguishes playable and unavailable programmes`() {
        assertEquals(
            MiniEpgArchiveState.AVAILABLE,
            miniEpgArchiveState(archiveChannel, programme, nowMs = 20_000L, available = true),
        )
        assertEquals(
            MiniEpgArchiveState.UNAVAILABLE,
            miniEpgArchiveState(archiveChannel, programme, nowMs = 20_000L, available = false),
        )
        assertEquals(
            MiniEpgArchiveState.UNAVAILABLE,
            miniEpgArchiveState(archiveChannel.copy(archive = null), programme, nowMs = 20_000L, available = null),
        )
    }

    @Test
    fun `OK plays only a confirmed available past programme`() {
        assertEquals(
            MiniEpgSelectionAction.PLAY_ARCHIVE,
            miniEpgSelectionAction(programme, nowMs = 20_000L, archiveState = MiniEpgArchiveState.AVAILABLE),
        )
        assertEquals(
            MiniEpgSelectionAction.IGNORE,
            miniEpgSelectionAction(programme, nowMs = 20_000L, archiveState = MiniEpgArchiveState.UNAVAILABLE),
        )
        assertEquals(
            MiniEpgSelectionAction.IGNORE,
            miniEpgSelectionAction(programme, nowMs = 20_000L, archiveState = MiniEpgArchiveState.LOADING),
        )
        assertEquals(
            MiniEpgSelectionAction.SELECT_LIVE,
            miniEpgSelectionAction(programme, nowMs = 19_999L, archiveState = MiniEpgArchiveState.NOT_APPLICABLE),
        )
    }
}
