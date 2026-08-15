package sk.ziacik.androidtvplayer.channel

import org.junit.Assert.assertEquals
import org.junit.Test

class TvChannelTest {
    @Test
    fun `catalogue contains Jednotka then Dvojka with STVR ids and display names`() {
        assertEquals(
            listOf(TvChannel.JEDNOTKA, TvChannel.DVOJKA),
            TvChannel.entries.toList(),
        )
        assertEquals("1", TvChannel.JEDNOTKA.stvrId)
        assertEquals("2", TvChannel.DVOJKA.stvrId)
        assertEquals("JEDNOTKA", TvChannel.JEDNOTKA.displayName)
        assertEquals("DVOJKA", TvChannel.DVOJKA.displayName)
    }

    @Test
    fun `next and previous wrap around the channel catalogue`() {
        assertEquals(TvChannel.DVOJKA, TvChannel.JEDNOTKA.next())
        assertEquals(TvChannel.JEDNOTKA, TvChannel.DVOJKA.next())
        assertEquals(TvChannel.DVOJKA, TvChannel.JEDNOTKA.previous())
        assertEquals(TvChannel.JEDNOTKA, TvChannel.DVOJKA.previous())
    }

    @Test
    fun `storage keys decode and unknown keys default to Jednotka`() {
        assertEquals("jednotka", TvChannel.JEDNOTKA.storageKey)
        assertEquals("dvojka", TvChannel.DVOJKA.storageKey)
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromStorageKey("jednotka"))
        assertEquals(TvChannel.DVOJKA, TvChannel.fromStorageKey("dvojka"))
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromStorageKey("invalid"))
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromStorageKey(null))
    }
}
