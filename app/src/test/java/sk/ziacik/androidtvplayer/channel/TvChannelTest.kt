package sk.ziacik.androidtvplayer.channel

import org.junit.Assert.assertEquals
import org.junit.Test

class TvChannelTest {
    @Test
    fun `catalogue position is one based and rejects unavailable positions`() {
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromChannelNumber(1))
        assertEquals(TvChannel.DVOJKA, TvChannel.fromChannelNumber(2))
        assertEquals(TvChannel.entries[11], TvChannel.fromChannelNumber(12))
        assertEquals(null, TvChannel.fromChannelNumber(0))
        assertEquals(null, TvChannel.fromChannelNumber(TvChannel.entries.size + 1))
    }

    @Test
    fun `catalogue retains base channels and includes the Freeview additions`() {
        assertEquals(
            listOf(TvChannel.JEDNOTKA, TvChannel.DVOJKA, TvChannel.MARKIZA),
            TvChannel.entries.take(3),
        )
        assertEquals(40, TvChannel.entries.size)
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
        assertEquals(TvChannel.STVR_24, TvChannel.MARKIZA.next())
        assertEquals(TvChannel.JEDNOTKA, TvChannel.BBC_FOOD.next())
        assertEquals(TvChannel.BBC_FOOD, TvChannel.JEDNOTKA.previous())
        assertEquals(TvChannel.MARKIZA, TvChannel.STVR_24.previous())
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
