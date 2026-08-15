package sk.ziacik.androidtvplayer.player

import sk.ziacik.androidtvplayer.channel.TvChannel
import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

sealed interface PlayerUiState {
    val channel: TvChannel

    data class Resolving(override val channel: TvChannel) : PlayerUiState

    data class Preparing(override val channel: TvChannel) : PlayerUiState

    data class Ready(
        override val channel: TvChannel,
        val program: ProgramMetadata,
        val playback: PlaybackSnapshot,
    ) : PlayerUiState

    data class Unavailable(
        override val channel: TvChannel,
        val program: ProgramMetadata,
    ) : PlayerUiState

    data class Error(
        override val channel: TvChannel,
        val message: String,
    ) : PlayerUiState
}
