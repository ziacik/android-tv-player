package sk.ziacik.androidtvplayer.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OverlayController(
    private val scope: CoroutineScope,
) {
    private val mutableVisible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = mutableVisible.asStateFlow()

    private var hideJob: Job? = null
    private var waitingForProgramTitle = false

    fun show(durationMs: Long = NORMAL_TIMEOUT_MS) {
        waitingForProgramTitle = false
        mutableVisible.value = true
        scheduleHide(durationMs)
    }

    fun showUntilProgramTitleReady() {
        waitingForProgramTitle = true
        mutableVisible.value = true
        hideJob?.cancel()
        hideJob = null
    }

    fun onProgramTitleReady() {
        if (!waitingForProgramTitle || !mutableVisible.value) return
        waitingForProgramTitle = false
        scheduleHide(NORMAL_TIMEOUT_MS)
    }

    fun hide() {
        waitingForProgramTitle = false
        hideJob?.cancel()
        hideJob = null
        mutableVisible.value = false
    }

    private fun scheduleHide(durationMs: Long) {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(durationMs)
            mutableVisible.value = false
        }
    }

    companion object {
        const val NORMAL_TIMEOUT_MS = 6_000L
        const val OK_TIMEOUT_MS = 60_000L
    }
}
