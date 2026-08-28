package sk.ziacik.androidtvplayer.systemepg

import android.content.Context
import java.io.File
import sk.ziacik.androidtvplayer.channel.ChannelCatalog
import sk.ziacik.androidtvplayer.channel.ChannelCatalogRepository
import sk.ziacik.androidtvplayer.channel.OkHttpChannelCatalogDownloader
import sk.ziacik.androidtvplayer.channel.TvChannel

class KanalikCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val repository = ChannelCatalogRepository(
        seed = { appContext.assets.open("channels.json").bufferedReader().use { it.readText() } },
        cacheFile = File(appContext.filesDir, "channels.json"),
        download = OkHttpChannelCatalogDownloader(CHANNELS_URL)::download,
    )

    fun load(): ChannelCatalog = ChannelCatalog(repository.load().channels + TvChannel.sweetTvChannels)

    private companion object {
        const val CHANNELS_URL = "https://raw.githubusercontent.com/ziacik/android-tv-player/master/channels.json"
    }
}
