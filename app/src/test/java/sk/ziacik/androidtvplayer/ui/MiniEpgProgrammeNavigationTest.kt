package sk.ziacik.androidtvplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
    fun `past STVR programme is archiveable even when live stream provider is direct`() {
        val directJednotka = TvChannel(
            storageKey = "jednotka",
            stvrId = "1",
            displayName = "JEDNOTKA",
            provider = ChannelProvider.DIRECT,
            providerValue = "https://example.com/live.m3u8",
        )

        assertTrue(canPlayStvrArchive(directJednotka, programme, nowMs = 20_000L))
    }
}
