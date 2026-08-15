package sk.ziacik.androidtvplayer.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sk.ziacik.androidtvplayer.resolver.StreamSource

class PlayerController(
    private val scope: CoroutineScope,
    private val resolve: suspend () -> StreamSource,
    private val playerPort: PlayerPort,
    private val diagnostics: (message: String, cause: Throwable?) -> Unit = { _, _ -> },
) {
    private val mutableState = MutableStateFlow<PlayerUiState>(PlayerUiState.Resolving)
    val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    private var resolveJob: Job? = null

    init {
        playerPort.setListener(
            object : PlayerPort.Listener {
                override fun onReady(isPlaying: Boolean) {
                    mutableState.value = PlayerUiState.Ready(
                        isPlaying = isPlaying,
                        isSeekable = playerPort.isSeekable,
                    )
                }

                override fun onPlayingChanged(isPlaying: Boolean) {
                    val current = mutableState.value
                    if (current is PlayerUiState.Ready) {
                        mutableState.value = current.copy(isPlaying = isPlaying)
                    }
                }

                override fun onError(message: String) {
                    diagnostics("Media3 playback failed: $message", null)
                    mutableState.value = PlayerUiState.Error(ERROR_MESSAGE)
                }
            },
        )
    }

    fun start() = retry()

    fun retry() {
        resolveJob?.cancel()
        resolveJob = scope.launch {
            mutableState.value = PlayerUiState.Resolving
            try {
                val source = resolve()
                mutableState.value = PlayerUiState.Preparing
                playerPort.load(source)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics("STVR resolve failed", error)
                mutableState.value = PlayerUiState.Error(ERROR_MESSAGE)
            }
        }
    }

    fun seekBack() {
        if (!playerPort.isSeekable) return
        playerPort.seekTo((playerPort.currentPositionMs - SEEK_INCREMENT_MS).coerceAtLeast(0L))
    }

    fun seekForward() {
        if (!playerPort.isSeekable) return
        val requested = playerPort.currentPositionMs + SEEK_INCREMENT_MS
        val target = playerPort.durationMs?.let { duration ->
            requested.coerceIn(0L, duration.coerceAtLeast(0L))
        } ?: requested
        playerPort.seekTo(target)
    }

    fun togglePlayback() {
        if (playerPort.isPlaying) playerPort.pause() else playerPort.play()
    }

    fun goLive() {
        playerPort.goLive()
    }

    fun release() {
        resolveJob?.cancel()
        playerPort.release()
    }

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
        const val ERROR_MESSAGE = "Stream sa nepodarilo načítať"
    }
}
