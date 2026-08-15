package sk.ziacik.androidtvplayer.ui

import java.util.Locale
import sk.ziacik.androidtvplayer.player.PlayerUiState

data class PlayerOverlayModel(
    val channelLabel: String,
    val programTitle: String,
    val progress: Float?,
    val delayText: String?,
    val isLive: Boolean,
    val isPlaying: Boolean,
    val isSeekable: Boolean,
    val liveActionText: String,
) {
    companion object {
        fun from(state: PlayerUiState.Ready): PlayerOverlayModel {
            val playback = state.playback
            val duration = playback.durationMs
            val validWindow = playback.isSeekable && duration != null && duration > 0L
            val progress = if (validWindow) {
                (playback.currentPositionMs.toDouble() / duration.toDouble())
                    .coerceIn(0.0, 1.0)
                    .toFloat()
            } else {
                null
            }
            val offset = playback.liveOffsetMs
                ?: duration
                    ?.minus(playback.currentPositionMs)
                    ?.coerceAtLeast(0L)
            val isLive = offset != null && offset <= LIVE_THRESHOLD_MS

            return PlayerOverlayModel(
                channelLabel = "JEDNOTKA · NAŽIVO",
                programTitle = state.program.title,
                progress = progress,
                delayText = offset
                    ?.takeUnless { isLive || !validWindow }
                    ?.let(::formatDelay),
                isLive = isLive,
                isPlaying = playback.isPlaying,
                isSeekable = playback.isSeekable,
                liveActionText = if (isLive) "NAŽIVO" else "NA LIVE",
            )
        }

        private fun formatDelay(offsetMs: Long): String {
            val totalSeconds = offsetMs / 1_000L
            val hours = totalSeconds / 3_600L
            val minutes = (totalSeconds % 3_600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(
                    Locale.ROOT,
                    "−%d:%02d:%02d",
                    hours,
                    minutes,
                    seconds,
                )
            } else {
                String.format(Locale.ROOT, "−%d:%02d", minutes, seconds)
            }
        }

        private const val LIVE_THRESHOLD_MS = 10_000L
    }
}
