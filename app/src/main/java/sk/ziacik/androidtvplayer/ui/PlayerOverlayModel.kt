package sk.ziacik.androidtvplayer.ui

import java.util.Locale
import sk.ziacik.androidtvplayer.player.PlayerUiState
import sk.ziacik.androidtvplayer.player.PlaybackSnapshot
import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

enum class PlayerOverlayStateIndicator { LIVE, SWITCHING, RETRYING }

internal fun formatStreamInfoLabel(
    width: Int?,
    height: Int?,
    bitrate: Int?,
    streamHost: String? = null,
): String? {
    val resolution = if (width != null && width > 0 && height != null && height > 0) {
        "${width}×${height}"
    } else {
        null
    }
    val bitrateLabel = bitrate
        ?.takeIf { it > 0 }
        ?.let { String.format(Locale.US, "%.1f Mbps", it / 1_000_000.0) }
    return listOfNotNull(
        resolution,
        bitrateLabel,
        streamHost?.takeIf { it.isNotBlank() },
    )
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
}

data class PlayerOverlayModel(
    val channelLabel: String,
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
            streamHost: String? = null,
        ): PlayerOverlayModel = from(
            channel = state.channel,
            program = state.program,
            playback = state.playback,
            streamHost = streamHost,
            statusText = null,
            nowMs = nowMs,
        )

        fun from(
            channel: TvChannel,
            program: ProgramMetadata?,
            playback: PlaybackSnapshot?,
            streamHost: String? = null,
            statusText: String?,
            nowMs: Long,
        ): PlayerOverlayModel {
            val startsAtMs = program?.startsAtMs
            val endsAtMs = program?.endsAtMs
            val hasProgrammeInterval =
                startsAtMs != null &&
                endsAtMs != null &&
                endsAtMs > startsAtMs
            val offset = playback?.let { snapshot ->
                snapshot.liveOffsetMs
                    ?: snapshot.durationMs
                        ?.minus(snapshot.currentPositionMs)
                        ?.coerceAtLeast(0L)
            }
            val watchedNowMs = offset?.let(nowMs::minus) ?: nowMs
            val timelineStartMs = startsAtMs?.coerceAtMost(watchedNowMs)
            val streamProgress = playback
                ?.durationMs
                ?.takeIf { it > 0L }
                ?.let { durationMs ->
                    (playback.currentPositionMs.toDouble() / durationMs.toDouble())
                        .coerceIn(0.0, 1.0)
                        .toFloat()
                }
            val progress = if (hasProgrammeInterval) {
                ((watchedNowMs - timelineStartMs!!).toDouble() / (endsAtMs - timelineStartMs).toDouble())
                    .coerceIn(0.0, 1.0)
                    .toFloat()
            } else {
                streamProgress
            }
            val isLive = offset != null && offset <= LIVE_THRESHOLD_MS
            val stateIndicator = when (statusText) {
                null -> PlayerOverlayStateIndicator.LIVE
                "Prepínam…" -> PlayerOverlayStateIndicator.SWITCHING
                else -> PlayerOverlayStateIndicator.RETRYING
            }
            val baseChannelLabel = "${channel.ordinal + 1} · ${channel.displayName}"
            val streamInfoLabel = formatStreamInfoLabel(
                width = playback?.videoWidth,
                height = playback?.videoHeight,
                bitrate = playback?.videoBitrate,
                streamHost = streamHost,
            )

            return PlayerOverlayModel(
                channelLabel = listOfNotNull(baseChannelLabel, streamInfoLabel).joinToString("    "),
                programTitle = if (program?.isEpgLookupPending == true) "" else program?.title.orEmpty(),
                progress = progress,
                displayNowMs = nowMs,
                programmeStartMs = timelineStartMs.takeIf { hasProgrammeInterval },
                programmeNowMs = watchedNowMs.takeIf { hasProgrammeInterval },
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
