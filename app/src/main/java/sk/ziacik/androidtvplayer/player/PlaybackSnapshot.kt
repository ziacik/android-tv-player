package sk.ziacik.androidtvplayer.player

data class PlaybackSnapshot(
    val currentPositionMs: Long,
    val durationMs: Long?,
    val liveOffsetMs: Long?,
    val isSeekable: Boolean,
    val isPlaying: Boolean,
)
