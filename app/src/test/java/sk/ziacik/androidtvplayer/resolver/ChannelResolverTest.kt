package sk.ziacik.androidtvplayer.resolver

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel

class ChannelResolverTest {
    @Test
    fun `routes STVR and Markíza channels to their own resolvers`() = runTest {
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
        )

        resolver.resolve(TvChannel.JEDNOTKA)
        resolver.resolve(TvChannel.DVOJKA)
        val markiza = resolver.resolve(TvChannel.MARKIZA)

        assertEquals(
            listOf(TvChannel.JEDNOTKA, TvChannel.DVOJKA, TvChannel.MARKIZA),
            calls,
        )
        assertEquals(StreamResolution.RequiresCredentials(TvChannel.MARKIZA), markiza)
    }
}
