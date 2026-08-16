package sk.ziacik.androidtvplayer.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sk.ziacik.androidtvplayer.channel.TvChannel

class NumericChannelInput(
    private val scope: CoroutineScope,
    private val selectionDelayMs: Long = 1_000L,
    private val onChannelSelected: (TvChannel) -> Unit,
) {
    private val mutableDigits = MutableStateFlow<String?>(null)
    val digits: StateFlow<String?> = mutableDigits.asStateFlow()

    private var selectionJob: Job? = null

    fun append(digit: Int) {
        require(digit in 0..9)
        mutableDigits.value = "${mutableDigits.value.orEmpty()}$digit"
        selectionJob?.cancel()
        selectionJob = scope.launch {
            delay(selectionDelayMs)
            val completedDigits = mutableDigits.value
            mutableDigits.value = null
            completedDigits
                ?.toIntOrNull()
                ?.let(TvChannel::fromChannelNumber)
                ?.let(onChannelSelected)
        }
    }

    fun cancel() {
        selectionJob?.cancel()
        selectionJob = null
        mutableDigits.value = null
    }
}
