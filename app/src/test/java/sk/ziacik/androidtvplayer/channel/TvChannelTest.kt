package sk.ziacik.androidtvplayer.channel

import org.junit.Assert.assertEquals
import org.junit.Test

class TvChannelTest {
    @Test
    fun `catalogue contains Jednotka Dvojka then Markíza`() {
        assertEquals(
            listOf(TvChannel.JEDNOTKA, TvChannel.DVOJKA, TvChannel.MARKIZA),
            TvChannel.entries.toList(),
        )
        assertEquals("1", TvChannel.JEDNOTKA.stvrId)
        assertEquals("2", TvChannel.DVOJKA.stvrId)
        assertEquals("JEDNOTKA", TvChannel.JEDNOTKA.displayName)
        assertEquals("DVOJKA", TvChannel.DVOJKA.displayName)
        assertEquals("markiza", TvChannel.MARKIZA.storageKey)
        assertEquals(null, TvChannel.MARKIZA.stvrId)
        assertEquals("MARKÍZA", TvChannel.MARKIZA.displayName)
    }

    @Test
    fun `next and previous wrap around the channel catalogue`() {
        assertEquals(TvChannel.DVOJKA, TvChannel.JEDNOTKA.next())
        assertEquals(TvChannel.MARKIZA, TvChannel.DVOJKA.next())
        assertEquals(TvChannel.JEDNOTKA, TvChannel.MARKIZA.next())
        assertEquals(TvChannel.MARKIZA, TvChannel.JEDNOTKA.previous())
        assertEquals(TvChannel.DVOJKA, TvChannel.MARKIZA.previous())
    }

    @Test
    fun `storage keys decode and unknown keys default to Jednotka`() {
        assertEquals("jednotka", TvChannel.JEDNOTKA.storageKey)
        assertEquals("dvojka", TvChannel.DVOJKA.storageKey)
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromStorageKey("jednotka"))
        assertEquals(TvChannel.DVOJKA, TvChannel.fromStorageKey("dvojka"))
        assertEquals(TvChannel.MARKIZA, TvChannel.fromStorageKey("markiza"))
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromStorageKey("invalid"))
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromStorageKey(null))
    }
}
