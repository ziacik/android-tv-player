package sk.ziacik.androidtvplayer.player

import sk.ziacik.androidtvplayer.resolver.StreamSource

interface PlayerPort {
    fun snapshot(): PlaybackSnapshot

    fun load(loadId: Long, source: StreamSource)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun goLive()
    fun stop()
    fun release()
    fun setListener(listener: Listener)

    interface Listener {
        fun onReady(loadId: Long, isPlaying: Boolean)
        fun onPlayingChanged(loadId: Long, isPlaying: Boolean)
        fun onError(loadId: Long, message: String)
    }
}
