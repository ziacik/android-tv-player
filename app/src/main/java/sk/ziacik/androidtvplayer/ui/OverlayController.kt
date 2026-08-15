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
    private val autoHideMs: Long = 4_000L,
) {
    private val mutableVisible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = mutableVisible.asStateFlow()

    private var hideJob: Job? = null

    fun show() {
        mutableVisible.value = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(autoHideMs)
            mutableVisible.value = false
        }
    }

    fun hide() {
        hideJob?.cancel()
        mutableVisible.value = false
    }
}

