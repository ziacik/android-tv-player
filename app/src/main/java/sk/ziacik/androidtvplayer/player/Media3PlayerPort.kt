package sk.ziacik.androidtvplayer.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import sk.ziacik.androidtvplayer.resolver.StreamSource

@androidx.annotation.OptIn(UnstableApi::class)
class Media3PlayerPort(context: Context) : PlayerPort {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private var listener: PlayerPort.Listener? = null
    private var activePlayerListener: Player.Listener? = null

    override fun snapshot() = PlaybackSnapshot(
        currentPositionMs = player.currentPosition.coerceAtLeast(0L),
        durationMs = player.duration
            .takeUnless { duration -> duration == C.TIME_UNSET || duration < 0L },
        liveOffsetMs = player.currentLiveOffset
            .takeUnless { offset -> offset == C.TIME_UNSET || offset < 0L },
        isSeekable = player.isCurrentMediaItemSeekable,
        isPlaying = player.isPlaying,
    )

    override fun load(loadId: Long, source: StreamSource) {
        val loadListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    listener?.onReady(loadId, player.isPlaying)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener?.onPlayingChanged(loadId, isPlaying)
            }

            override fun onAvailableCommandsChanged(
                availableCommands: Player.Commands,
            ) {
                if (player.playbackState == Player.STATE_READY) {
                    listener?.onReady(loadId, player.isPlaying)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                listener?.onError(loadId, error.errorCodeName)
            }
        }
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(source.userAgent)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val mediaItem = MediaItem.Builder()
            .setUri(source.url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        detachActivePlayerListener()
        try {
            player.setMediaSource(mediaSourceFactory.createMediaSource(mediaItem))
            activePlayerListener = loadListener
            player.addListener(loadListener)
            player.prepare()
            player.playWhenReady = true
        } catch (error: Exception) {
            detachActivePlayerListener()
            throw error
        }
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun goLive() {
        player.seekToDefaultPosition()
        player.play()
    }

    override fun stop() {
        detachActivePlayerListener()
        player.stop()
        player.clearMediaItems()
    }

    override fun release() {
        detachActivePlayerListener()
        player.release()
    }

    override fun setListener(listener: PlayerPort.Listener) {
        this.listener = listener
    }

    private fun detachActivePlayerListener() {
        activePlayerListener?.let(player::removeListener)
        activePlayerListener = null
    }
}
