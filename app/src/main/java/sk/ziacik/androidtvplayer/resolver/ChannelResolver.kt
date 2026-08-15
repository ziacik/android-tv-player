package sk.ziacik.androidtvplayer.resolver

import sk.ziacik.androidtvplayer.channel.TvChannel

class ChannelResolver(
    private val resolveStvr: suspend (TvChannel) -> StreamResolution,
    private val resolveMarkiza: suspend (TvChannel) -> StreamResolution,
) {
    suspend fun resolve(channel: TvChannel): StreamResolution = when (channel) {
        TvChannel.JEDNOTKA,
        TvChannel.DVOJKA,
        -> resolveStvr(channel)
        TvChannel.MARKIZA -> resolveMarkiza(channel)
    }
}
