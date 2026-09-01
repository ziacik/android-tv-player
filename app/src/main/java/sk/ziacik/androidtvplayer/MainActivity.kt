package sk.ziacik.androidtvplayer

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sk.ziacik.androidtvplayer.channel.SharedPreferencesChannelStore
import sk.ziacik.androidtvplayer.channel.ChannelCatalogRepository
import sk.ziacik.androidtvplayer.channel.ChannelCatalog
import sk.ziacik.androidtvplayer.channel.OkHttpChannelCatalogDownloader
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.channel.EpgSourceId
import sk.ziacik.androidtvplayer.epg.CachedXmltvEpgRepository
import sk.ziacik.androidtvplayer.epg.OkHttpEpgDownloader
import sk.ziacik.androidtvplayer.epg.XmltvEpgSource
import sk.ziacik.androidtvplayer.player.Media3PlayerPort
import sk.ziacik.androidtvplayer.player.PlayerController
import sk.ziacik.androidtvplayer.resolver.ChannelResolver
import sk.ziacik.androidtvplayer.resolver.CnnPrimaNewsResolver
import sk.ziacik.androidtvplayer.resolver.CtResolver
import sk.ziacik.androidtvplayer.resolver.DirectResolver
import sk.ziacik.androidtvplayer.resolver.JojResolver
import sk.ziacik.androidtvplayer.resolver.OkHttpFreeviewClient
import sk.ziacik.androidtvplayer.resolver.OkHttpStvrClient
import sk.ziacik.androidtvplayer.resolver.NovaResolver
import sk.ziacik.androidtvplayer.resolver.StvrResolver
import sk.ziacik.androidtvplayer.resolver.SweetTvResolver
import sk.ziacik.androidtvplayer.resolver.Ta3Resolver
import sk.ziacik.androidtvplayer.ui.AndroidTvPlayerTheme
import sk.ziacik.androidtvplayer.ui.OverlayController
import sk.ziacik.androidtvplayer.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val playerPort = Media3PlayerPort(this)
        val freeviewHttpClient = OkHttpFreeviewClient()
        val resolver = ChannelResolver(
            resolveStvr = StvrResolver(OkHttpStvrClient())::resolve,
            resolveJoj = JojResolver(freeviewHttpClient)::resolve,
            resolveCt = CtResolver(freeviewHttpClient)::resolve,
            resolveTa3 = Ta3Resolver(freeviewHttpClient)::resolve,
            resolveNova = NovaResolver(freeviewHttpClient)::resolve,
            resolveCnnPrimaNews = CnnPrimaNewsResolver(freeviewHttpClient)::resolve,
            resolveSweetTv = SweetTvResolver(freeviewHttpClient)::resolve,
            resolveDirect = DirectResolver()::resolve,
        )
        val catalogRepository = ChannelCatalogRepository(
            seed = {
                runCatching {
                    assets.open("channels.json").bufferedReader().use { it.readText() }
                }.getOrNull()
            },
            cacheFile = File(filesDir, "channels.json"),
            download = OkHttpChannelCatalogDownloader(CHANNELS_URL)::download,
        )
        val catalog = ChannelCatalog(
            catalogRepository.loadOrNull()?.channels.orEmpty() + TvChannel.sweetTvChannels,
        )
        TvChannel.setRuntimeEntries(catalog.channels)
        val channelStore = SharedPreferencesChannelStore(this)
        val epgDirectory = File(filesDir, "epg")
        File(epgDirectory, "iptv-org-cz.xml.gz").delete()
        val epgRepository = CachedXmltvEpgRepository(
            sources = listOf(
                XmltvEpgSource(
                    id = EpgSourceId.OPEN_EPG,
                    cacheFile = File(epgDirectory, "open-epg.xml.gz"),
                    download = OkHttpEpgDownloader(OPEN_EPG_URL)::download,
                ),
                XmltvEpgSource(
                    id = EpgSourceId.SKYLINK,
                    cacheFile = File(epgDirectory, "skylink-a3b-a1.xml"),
                    download = OkHttpEpgDownloader(SKYLINK_EPG_URL)::download,
                ),
            ),
            diagnostics = { message, cause ->
                Log.e("AndroidTvPlayer", message, cause)
            },
        )
        val overlayController = OverlayController(appScope)
        playerController = PlayerController(
            scope = appScope,
            initialChannel = channelStore.load(catalog),
            resolve = resolver::resolve,
            playerPort = playerPort,
            epgRepository = epgRepository,
            onChannelSelected = channelStore::save,
            diagnostics = { message, cause ->
                Log.e("AndroidTvPlayer", message, cause)
            },
        )
        appScope.launch {
            runCatching {
                ChannelCatalog(
                    catalogRepository.refresh().channels + TvChannel.sweetTvChannels,
                )
            }
                .onSuccess { refreshedCatalog ->
                    val changed = TvChannel.entries != refreshedCatalog.channels
                    TvChannel.setRuntimeEntries(refreshedCatalog.channels)
                    if (changed) playerController.retry()
                }
                .onFailure { Log.w("AndroidTvPlayer", "Channel catalog refresh failed") }
        }

        setContent {
            AndroidTvPlayerTheme {
                PlayerScreen(
                    controller = playerController,
                    player = playerPort.player,
                    overlayController = overlayController,
                    epgRepository = epgRepository,
                    onExit = ::finish,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playerController.start()
    }

    override fun onStop() {
        playerController.stop()
        super.onStop()
    }

    override fun onDestroy() {
        playerController.release()
        appScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val SKYLINK_EPG_URL =
            "https://raw.githubusercontent.com/370network/skylink-xmltv/refs/heads/main/a3b_a1.xml"
        const val OPEN_EPG_URL = "https://www.open-epg.com/generate/jnapkTB7Wq.xml.gz"
        const val CHANNELS_URL = "https://plainraw.com/json/cbc833011422"
    }
}
