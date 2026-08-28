package sk.ziacik.androidtvplayer.ui

import java.util.Locale
import sk.ziacik.androidtvplayer.player.PlayerUiState
import sk.ziacik.androidtvplayer.player.PlaybackSnapshot
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

enum class PlayerOverlayStateIndicator { LIVE, SWITCHING, RETRYING }

internal fun formatStreamInfoLabel(width: Int?, height: Int?, bitrate: Int?): String? {
    val resolution = if (width != null && width > 0 && height != null && height > 0) {
        "${width}×${height}"
    } else {
        null
    }
    val bitrateLabel = bitrate
        ?.takeIf { it > 0 }
        ?.let { String.format(Locale.US, "%.1f Mbps", it / 1_000_000.0) }
    return listOfNotNull(resolution, bitrateLabel)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
}

data class PlayerOverlayModel(
    val channelLabel: String,
    val streamInfoLabel: String? = null,
    val programTitle: String,
    val progress: Float?,
    val displayNowMs: Long = 0L,
    val programmeStartMs: Long?,
    val programmeNowMs: Long?,
    val programmeEndMs: Long?,
    val isLive: Boolean,
    val isPlaying: Boolean,
    val isSeekable: Boolean,
    val liveActionText: String,
    val statusText: String? = null,
    val stateIndicator: PlayerOverlayStateIndicator = PlayerOverlayStateIndicator.LIVE,
) {
    companion object {
        fun from(
            state: PlayerUiState.Ready,
            nowMs: Long,
        ): PlayerOverlayModel = from(
            channel = state.channel,
            program = state.program,
            playback = state.playback,
            statusText = null,
            nowMs = nowMs,
        )

        fun from(
            channel: TvChannel,
            program: ProgramMetadata?,
            playback: PlaybackSnapshot?,
            statusText: String?,
            nowMs: Long,
        ): PlayerOverlayModel {
            val startsAtMs = program?.startsAtMs
            val endsAtMs = program?.endsAtMs
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
            val offset = playback?.let { snapshot ->
                snapshot.liveOffsetMs
                    ?: snapshot.durationMs
                        ?.minus(snapshot.currentPositionMs)
                        ?.coerceAtLeast(0L)
            }
            val isLive = offset != null && offset <= LIVE_THRESHOLD_MS
            val stateIndicator = when (statusText) {
                null -> PlayerOverlayStateIndicator.LIVE
                "Prepínam…" -> PlayerOverlayStateIndicator.SWITCHING
                else -> PlayerOverlayStateIndicator.RETRYING
            }

            return PlayerOverlayModel(
                channelLabel = "${channel.ordinal + 1} · ${channel.displayName}",
                streamInfoLabel = playback?.let { snapshot ->
                    formatStreamInfoLabel(
                        width = snapshot.videoWidth,
                        height = snapshot.videoHeight,
                        bitrate = snapshot.videoBitrate,
                    )
                },
                programTitle = program?.title.orEmpty(),
                progress = progress,
                displayNowMs = nowMs,
                programmeStartMs = timelineStartMs.takeIf { hasProgrammeInterval },
                programmeNowMs = nowMs.takeIf { hasProgrammeInterval },
                programmeEndMs = endsAtMs.takeIf { hasProgrammeInterval },
                isLive = stateIndicator == PlayerOverlayStateIndicator.LIVE && isLive,
                isPlaying = playback?.isPlaying == true,
                isSeekable = playback?.isSeekable == true,
                liveActionText = "NAŽIVO",
                statusText = statusText,
                stateIndicator = stateIndicator,
            )
        }

        private const val LIVE_THRESHOLD_MS = 10_000L
    }
}
