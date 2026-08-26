package sk.ziacik.androidtvplayer.channel

interface ChannelStore {
    fun load(catalog: ChannelCatalog): TvChannel

    fun save(channel: TvChannel)
}
