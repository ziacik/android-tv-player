package sk.ziacik.androidtvplayer.player

import sk.ziacik.androidtvplayer.resolver.StreamSource

interface PlayerPort {
    fun snapshot(): PlaybackSnapshot

    fun load(source: StreamSource)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun goLive()
    fun release()
    fun setListener(listener: Listener)

    interface Listener {
        fun onReady(isPlaying: Boolean)
        fun onPlayingChanged(isPlaying: Boolean)
        fun onError(message: String)
    }
}
