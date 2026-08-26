package sk.ziacik.androidtvplayer.ui

import sk.ziacik.androidtvplayer.player.PlayerUiState

data class PlayerOverlayModel(
    val channelLabel: String,
    val programTitle: String,
    val progress: Float?,
    val programmeStartMs: Long?,
    val programmeNowMs: Long?,
    val programmeEndMs: Long?,
    val isLive: Boolean,
    val isPlaying: Boolean,
    val isSeekable: Boolean,
    val liveActionText: String,
) {
    companion object {
        fun from(
            state: PlayerUiState.Ready,
            nowMs: Long,
        ): PlayerOverlayModel {
            val playback = state.playback
            val duration = playback.durationMs
            val startsAtMs = state.program.startsAtMs
            val endsAtMs = state.program.endsAtMs
            val hasProgrammeInterval =
                startsAtMs != null &&
                endsAtMs != null &&
                endsAtMs > startsAtMs
            val timelineStartMs = startsAtMs?.coerceAtMost(nowMs)
            val progress = if (hasProgrammeInterval) {
                ((nowMs - timelineStartMs!!).toDouble() / (endsAtMs - timelineStartMs).toDouble())
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
                channelLabel = "${state.channel.ordinal + 1} · ${state.channel.displayName} · NAŽIVO",
                programTitle = state.program.title,
                progress = progress,
                programmeStartMs = timelineStartMs.takeIf { hasProgrammeInterval },
                programmeNowMs = nowMs.takeIf { hasProgrammeInterval },
                programmeEndMs = endsAtMs.takeIf { hasProgrammeInterval },
                isLive = isLive,
                isPlaying = playback.isPlaying,
                isSeekable = playback.isSeekable,
                liveActionText = "NAŽIVO",
            )
        }

        private const val LIVE_THRESHOLD_MS = 10_000L
    }
}
