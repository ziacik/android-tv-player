package sk.ziacik.androidtvplayer.player

import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

sealed interface PlayerUiState {
    val channel: TvChannel

    data class Resolving(
        override val channel: TvChannel,
        val program: ProgramMetadata? = null,
    ) : PlayerUiState

    data class Preparing(
        override val channel: TvChannel,
        val program: ProgramMetadata? = null,
    ) : PlayerUiState

    data class Ready(
        override val channel: TvChannel,
        val program: ProgramMetadata,
        val playback: PlaybackSnapshot,
    ) : PlayerUiState

    data class Unavailable(
        override val channel: TvChannel,
        val program: ProgramMetadata,
        val nextRetryAtMs: Long? = null,
    ) : PlayerUiState

    data class CredentialsRequired(
        override val channel: TvChannel,
    ) : PlayerUiState

    data class Error(
        override val channel: TvChannel,
        val message: String,
        val reason: String,
        val nextRetryAtMs: Long? = null,
        val program: ProgramMetadata? = null,
    ) : PlayerUiState
}
