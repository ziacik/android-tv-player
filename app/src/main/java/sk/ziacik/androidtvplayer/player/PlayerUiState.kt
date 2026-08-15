package sk.ziacik.androidtvplayer.player

import sk.ziacik.androidtvplayer.resolver.ProgramMetadata

sealed interface PlayerUiState {
    data object Resolving : PlayerUiState
    data object Preparing : PlayerUiState
    data class Ready(
        val program: ProgramMetadata,
        val playback: PlaybackSnapshot,
    ) : PlayerUiState {
        val isPlaying: Boolean
            get() = playback.isPlaying

        val isSeekable: Boolean
            get() = playback.isSeekable
    }
    data class Unavailable(val program: ProgramMetadata) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}
