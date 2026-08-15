package sk.ziacik.androidtvplayer.ui

import android.view.KeyEvent

enum class FocusedControl {
    PLAY_PAUSE,
    LIVE,
}

sealed interface RemoteCommand {
    data object ShowOverlay : RemoteCommand
    data object SeekBack : RemoteCommand
    data object SeekForward : RemoteCommand
    data object TogglePlayback : RemoteCommand
    data object GoLive : RemoteCommand
    data object FocusPlayPause : RemoteCommand
    data object FocusLive : RemoteCommand
    data object HideOverlay : RemoteCommand
    data object Exit : RemoteCommand
    data object Ignore : RemoteCommand
}

class RemoteCommandMapper {
    fun map(
        keyCode: Int,
        overlayVisible: Boolean,
        focusedControl: FocusedControl,
    ): RemoteCommand = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> RemoteCommand.SeekBack
        KeyEvent.KEYCODE_DPAD_RIGHT -> RemoteCommand.SeekForward
        KeyEvent.KEYCODE_DPAD_UP -> RemoteCommand.FocusPlayPause
        KeyEvent.KEYCODE_DPAD_DOWN -> RemoteCommand.FocusLive
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> when {
            !overlayVisible -> RemoteCommand.ShowOverlay
            focusedControl == FocusedControl.LIVE -> RemoteCommand.GoLive
            else -> RemoteCommand.TogglePlayback
        }
        KeyEvent.KEYCODE_BACK -> if (overlayVisible) {
            RemoteCommand.HideOverlay
        } else {
            RemoteCommand.Exit
        }
        else -> RemoteCommand.Ignore
    }
}
