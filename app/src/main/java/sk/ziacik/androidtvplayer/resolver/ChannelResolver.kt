package sk.ziacik.androidtvplayer.resolver

import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.channel.ChannelProvider

class ChannelResolver(
    private val resolveStvr: suspend (TvChannel) -> StreamResolution,
    private val resolveMarkiza: suspend (TvChannel) -> StreamResolution,
    private val resolveJoj: suspend (TvChannel) -> StreamResolution,
    private val resolveCt: suspend (TvChannel) -> StreamResolution,
    private val resolveTa3: suspend (TvChannel) -> StreamResolution,
    private val resolveNova: suspend (TvChannel) -> StreamResolution,
    private val resolveCnnPrimaNews: suspend (TvChannel) -> StreamResolution,
    private val resolveSweetTv: suspend (TvChannel) -> StreamResolution,
    private val resolveDirect: suspend (TvChannel) -> StreamResolution,
) {
    suspend fun resolve(channel: TvChannel): StreamResolution = when (channel.provider) {
        ChannelProvider.STVR -> resolveStvr(channel)
        ChannelProvider.MARKIZA -> resolveMarkiza(channel)
        ChannelProvider.JOJ -> resolveJoj(channel)
        ChannelProvider.CT -> resolveCt(channel)
        ChannelProvider.TA3 -> resolveTa3(channel)
        ChannelProvider.NOVA -> resolveNova(channel)
        ChannelProvider.CNN_PRIMA_NEWS -> resolveCnnPrimaNews(channel)
        ChannelProvider.SWEET_TV -> resolveSweetTv(channel)
        ChannelProvider.DIRECT -> resolveDirect(channel)
    }
}
