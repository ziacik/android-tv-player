package sk.ziacik.androidtvplayer.player

sealed interface PlayerUiState {
    data object Resolving : PlayerUiState
    data object Preparing : PlayerUiState
    data class Ready(
        val isPlaying: Boolean,
        val isSeekable: Boolean,
    ) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

