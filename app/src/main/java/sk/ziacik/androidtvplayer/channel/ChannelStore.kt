package sk.ziacik.androidtvplayer.channel

interface ChannelStore {
    fun load(): TvChannel

    fun save(channel: TvChannel)
}
