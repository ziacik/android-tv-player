package sk.ziacik.androidtvplayer.channel

import org.junit.Assert.assertEquals
import org.junit.Test

class TvChannelTest {
    @Test
    fun `covered channels expose their XMLTV identifiers and remaining direct streams stay null`() {
        assertEquals(listOf("OPEN_EPG", "SKYLINK"), EpgSourceId.entries.map(EpgSourceId::name))
        assertEquals("336e46bf4276e77a716e494c6285d5db", TvChannel.MARKIZA.epgIds[EpgSourceId.SKYLINK])
        assertEquals("84a2364ade7443e6d6afe03f9aa2361a", TvChannel.JOJ.epgIds[EpgSourceId.SKYLINK])
        assertEquals("87a9a0429e3edc255adbb8601cfddc0a", TvChannel.LOVE_NATURE.epgIds[EpgSourceId.SKYLINK])
        assertEquals(null, TvChannel.WATERBEAR.epgIds[EpgSourceId.SKYLINK])
    }

    @Test
    fun `catalogue position is one based and rejects unavailable positions`() {
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromChannelNumber(1))
        assertEquals(TvChannel.DVOJKA, TvChannel.fromChannelNumber(2))
        assertEquals(TvChannel.entries[11], TvChannel.fromChannelNumber(12))
        assertEquals(null, TvChannel.fromChannelNumber(0))
        assertEquals(null, TvChannel.fromChannelNumber(TvChannel.entries.size + 1))
    }

    @Test
    fun `catalogue retains base channels and omits removed live and direct streams`() {
        assertEquals(
            listOf(TvChannel.JEDNOTKA, TvChannel.DVOJKA, TvChannel.MARKIZA),
            TvChannel.entries.take(3),
        )
        assertEquals(36, TvChannel.entries.size)
        assertEquals(
            emptySet<String>(),
            TvChannel.entries
                .map(TvChannel::storageKey)
                .intersect(setOf("stvr-live-o", "stvr-live", "nrsr", "ta3", "szts", "wild-earth", "bbc-food")),
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
    fun `sweet tv channels expose their anonymous channel ids`() {
        assertEquals(
            listOf(TvChannel.SVET_NARUBY, TvChannel.MEN_WITH_THE_POT),
            TvChannel.sweetTvChannels,
        )
        assertEquals(ChannelProvider.SWEET_TV, TvChannel.MEN_WITH_THE_POT.provider)
        assertEquals("2648", TvChannel.MEN_WITH_THE_POT.providerValue)
        assertEquals("MEN WITH THE POT", TvChannel.MEN_WITH_THE_POT.displayName)
    }

    @Test
    fun `legacy direct channel constants do not duplicate stream URLs`() {
        assertEquals("paprika-tv", TvChannel.PAPRIKA_TV.storageKey)
        assertEquals("PAPRIKA TV", TvChannel.PAPRIKA_TV.displayName)
        assertEquals(ChannelProvider.DIRECT, TvChannel.PAPRIKA_TV.provider)
        assertEquals(null, TvChannel.PAPRIKA_TV.providerValue)
        assertEquals(null, TvChannel.WATERBEAR.providerValue)
        assertEquals(null, TvChannel.LOVE_NATURE.providerValue)
        assertEquals(null, TvChannel.GUSTO_TV.providerValue)
        assertEquals(null, TvChannel.TASTEMADE.providerValue)
        assertEquals(null, TvChannel.TV5MONDE_CHEFS.providerValue)
    }

    @Test
    fun `next and previous wrap around the channel catalogue`() {
        assertEquals(TvChannel.DVOJKA, TvChannel.JEDNOTKA.next())
        assertEquals(TvChannel.MARKIZA, TvChannel.DVOJKA.next())
        assertEquals(TvChannel.STVR_24, TvChannel.MARKIZA.next())
        assertEquals(TvChannel.TV5MONDE_CHEFS, TvChannel.TASTEMADE.next())
        assertEquals(TvChannel.PAPRIKA_TV, TvChannel.TV5MONDE_CHEFS.next())
        assertEquals(TvChannel.SVET_NARUBY, TvChannel.PAPRIKA_TV.next())
        assertEquals(TvChannel.MEN_WITH_THE_POT, TvChannel.SVET_NARUBY.next())
        assertEquals(TvChannel.JEDNOTKA, TvChannel.MEN_WITH_THE_POT.next())
        assertEquals(TvChannel.MEN_WITH_THE_POT, TvChannel.JEDNOTKA.previous())
        assertEquals(TvChannel.SVET_NARUBY, TvChannel.MEN_WITH_THE_POT.previous())
        assertEquals(TvChannel.PAPRIKA_TV, TvChannel.SVET_NARUBY.previous())
        assertEquals(TvChannel.MARKIZA, TvChannel.STVR_24.previous())
    }

    @Test
    fun `storage keys decode and unknown keys default to Jednotka`() {
        assertEquals("jednotka", TvChannel.JEDNOTKA.storageKey)
        assertEquals("dvojka", TvChannel.DVOJKA.storageKey)
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromStorageKey("jednotka"))
        assertEquals(TvChannel.DVOJKA, TvChannel.fromStorageKey("dvojka"))
        assertEquals(TvChannel.MARKIZA, TvChannel.fromStorageKey("markiza"))
        assertEquals(TvChannel.PAPRIKA_TV, TvChannel.fromStorageKey("paprika-tv"))
        assertEquals(TvChannel.MEN_WITH_THE_POT, TvChannel.fromStorageKey("men-with-the-pot"))
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromStorageKey("invalid"))
        assertEquals(TvChannel.JEDNOTKA, TvChannel.fromStorageKey(null))
    }
}
