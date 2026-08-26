package sk.ziacik.androidtvplayer.resolver

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel

class ChannelResolverTest {
    @Test
    fun `routes every provider to its resolver`() = runTest {
        val calls = mutableListOf<TvChannel>()
        val resolver = ChannelResolver(
            resolveStvr = { channel ->
                calls += channel
                StreamResolution.Unavailable(ProgramMetadata("STVR", null, null, false))
            },
            resolveMarkiza = { channel ->
                calls += channel
                StreamResolution.RequiresCredentials(channel)
            },
            resolveJoj = { channel -> calls += channel; playable(channel) },
            resolveCt = { channel -> calls += channel; playable(channel) },
            resolveTa3 = { channel -> calls += channel; playable(channel) },
            resolveNova = { channel -> calls += channel; playable(channel) },
            resolveCnnPrimaNews = { channel -> calls += channel; playable(channel) },
            resolveSweetTv = { channel -> calls += channel; playable(channel) },
            resolveDirect = { channel -> calls += channel; playable(channel) },
        )

        resolver.resolve(TvChannel.JEDNOTKA)
        val markiza = resolver.resolve(TvChannel.MARKIZA)
        resolver.resolve(TvChannel.JOJ)
        resolver.resolve(TvChannel.CT_1)
        resolver.resolve(TvChannel.TA3)
        resolver.resolve(TvChannel.NOVA_CINEMA)
        resolver.resolve(TvChannel.CNN_PRIMA_NEWS)
        resolver.resolve(TvChannel.SVET_NARUBY)
        resolver.resolve(TvChannel.SZTS)

        assertEquals(
            listOf(
                TvChannel.JEDNOTKA,
                TvChannel.MARKIZA,
                TvChannel.JOJ,
                TvChannel.CT_1,
                TvChannel.TA3,
                TvChannel.NOVA_CINEMA,
                TvChannel.CNN_PRIMA_NEWS,
                TvChannel.SVET_NARUBY,
                TvChannel.SZTS,
            ),
            calls,
        )
        assertEquals(StreamResolution.RequiresCredentials(TvChannel.MARKIZA), markiza)
    }

    private fun playable(channel: TvChannel) = StreamResolution.Playable(
        ProgramMetadata(channel.displayName, null, null, true),
        StreamSource("https://example.com/${channel.storageKey}.m3u8", "test"),
    )
}
