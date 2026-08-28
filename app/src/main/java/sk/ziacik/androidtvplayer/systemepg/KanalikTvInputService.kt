package sk.ziacik.androidtvplayer.systemepg

import android.content.ComponentName
import android.content.Context
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvInputService
import android.net.Uri
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sk.ziacik.androidtvplayer.channel.ChannelProvider
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ChannelResolver
import sk.ziacik.androidtvplayer.resolver.CnnPrimaNewsResolver
import sk.ziacik.androidtvplayer.resolver.CtResolver
import sk.ziacik.androidtvplayer.resolver.DirectResolver
import sk.ziacik.androidtvplayer.resolver.JojResolver
import sk.ziacik.androidtvplayer.resolver.NovaResolver
import sk.ziacik.androidtvplayer.resolver.OkHttpFreeviewClient
import sk.ziacik.androidtvplayer.resolver.OkHttpStvrClient
import sk.ziacik.androidtvplayer.resolver.StvrResolver
import sk.ziacik.androidtvplayer.resolver.StreamManifest
import sk.ziacik.androidtvplayer.resolver.StreamResolution
import sk.ziacik.androidtvplayer.resolver.SweetTvResolver
import sk.ziacik.androidtvplayer.resolver.Ta3Resolver

class KanalikTvInputService : TvInputService() {
    override fun onCreateSession(inputId: String): Session = KanalikTvInputSession(this)
}

private class KanalikTvInputSession(context: Context) : TvInputService.Session(context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val catalog = KanalikCatalog(context)
    private val resolver = channelResolver()
    private var player: ExoPlayer? = null
    private var surface: Surface? = null
    private var tuneJob: Job? = null

    override fun onSetSurface(surface: Surface?): Boolean {
        this.surface = surface
        player?.setVideoSurface(surface)
        return true
    }

    override fun onTune(channelUri: Uri): Boolean {
        tuneJob?.cancel()
        player?.release()
        player = null
        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING)
        tuneJob = scope.launch {
            val channel = channelFor(channelUri)
            if (channel == null) {
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN)
                return@launch
            }
            when (val resolution = runCatching { resolver.resolve(channel) }.getOrNull()) {
                is StreamResolution.Playable -> play(resolution.source.url, resolution.source.userAgent, resolution.source.headers, resolution.source.manifest)
                is StreamResolution.Unavailable,
                is StreamResolution.RequiresCredentials,
                null -> notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_NOT_CONNECTED)
            }
        }
        return true
    }

    override fun onRelease() {
        tuneJob?.cancel()
        player?.release()
        scope.cancel()
    }

    override fun onSetCaptionEnabled(enabled: Boolean) = Unit

    override fun onSetStreamVolume(volume: Float) = Unit

    private fun channelFor(channelUri: Uri): TvChannel? {
        val storageKey = appContext.contentResolver.query(
            channelUri,
            arrayOf(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: return null
        return catalog.load().channels.firstOrNull { it.storageKey == storageKey }
    }

    private fun play(url: String, userAgent: String, headers: Map<String, String>, manifest: StreamManifest) {
        val dataSource = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(headers)
        val created = ExoPlayer.Builder(appContext).build()
        created.setVideoSurface(surface)
        created.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) notifyVideoAvailable()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN)
            }
        })
        val mimeType = if (manifest == StreamManifest.DASH) MimeTypes.APPLICATION_MPD else MimeTypes.APPLICATION_M3U8
        created.setMediaSource(DefaultMediaSourceFactory(dataSource).createMediaSource(MediaItem.Builder().setUri(url).setMimeType(mimeType).build()))
        created.prepare()
        created.play()
        player = created
    }

    private fun channelResolver(): ChannelResolver {
        val freeview = OkHttpFreeviewClient()
        return ChannelResolver(
            resolveStvr = StvrResolver(OkHttpStvrClient())::resolve,
            resolveJoj = JojResolver(freeview)::resolve,
            resolveCt = CtResolver(freeview)::resolve,
            resolveTa3 = Ta3Resolver(freeview)::resolve,
            resolveNova = NovaResolver(freeview)::resolve,
            resolveCnnPrimaNews = CnnPrimaNewsResolver(freeview)::resolve,
            resolveSweetTv = SweetTvResolver(freeview)::resolve,
            resolveDirect = DirectResolver()::resolve,
        )
    }
}
