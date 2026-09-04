package sk.ziacik.androidtvplayer.ui

import android.view.KeyEvent

enum class FocusedControl {
    TIMELINE,
    PLAY_PAUSE,
    LIVE,
}

sealed interface RemoteCommand {
    data class NumericDigit(val digit: Int) : RemoteCommand
    data object ChannelUp : RemoteCommand
    data object ChannelDown : RemoteCommand
    data object OpenMiniEpg : RemoteCommand
    data object MiniEpgUp : RemoteCommand
    data object MiniEpgDown : RemoteCommand
    data object SelectMiniEpgChannel : RemoteCommand
    data object CloseMiniEpg : RemoteCommand
    data object ShowOverlay : RemoteCommand
    data object SeekBack : RemoteCommand
    data object SeekForward : RemoteCommand
    data object TogglePlayback : RemoteCommand
    data object GoLive : RemoteCommand
    data object FocusPlayPause : RemoteCommand
    data object FocusLive : RemoteCommand
    data object FocusTimeline : RemoteCommand
    data object HideOverlay : RemoteCommand
    data object Exit : RemoteCommand
    data object Ignore : RemoteCommand
}

fun RemoteCommand.isChannelSelection(): Boolean =
    this is RemoteCommand.NumericDigit ||
        this == RemoteCommand.ChannelUp ||
        this == RemoteCommand.ChannelDown

fun RemoteCommand.overlayTimeoutMs(): Long? = when (this) {
    RemoteCommand.ShowOverlay -> OverlayController.OK_TIMEOUT_MS
    RemoteCommand.SeekBack,
    RemoteCommand.SeekForward,
    RemoteCommand.TogglePlayback,
    RemoteCommand.GoLive,
    RemoteCommand.FocusPlayPause,
    RemoteCommand.FocusLive,
    RemoteCommand.FocusTimeline,
    -> OverlayController.NORMAL_TIMEOUT_MS
    else -> null
}

class RemoteCommandMapper {
    fun map(
        keyCode: Int,
        overlayVisible: Boolean,
        focusedControl: FocusedControl,
        miniEpgVisible: Boolean = false,
    ): RemoteCommand {
        keyCode.toNumericDigit()?.let(RemoteCommand::NumericDigit)?.let { return it }

        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            -> return RemoteCommand.ChannelUp
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            -> return RemoteCommand.ChannelDown
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            -> return RemoteCommand.TogglePlayback
        }

        if (miniEpgVisible) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> RemoteCommand.MiniEpgUp
                KeyEvent.KEYCODE_DPAD_DOWN -> RemoteCommand.MiniEpgDown
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> RemoteCommand.SelectMiniEpgChannel
                KeyEvent.KEYCODE_BACK -> RemoteCommand.CloseMiniEpg
                else -> RemoteCommand.Ignore
            }
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> when (focusedControl) {
                FocusedControl.TIMELINE -> RemoteCommand.SeekBack
                FocusedControl.LIVE -> RemoteCommand.FocusPlayPause
                FocusedControl.PLAY_PAUSE -> RemoteCommand.FocusPlayPause
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> when (focusedControl) {
                FocusedControl.TIMELINE -> RemoteCommand.SeekForward
                FocusedControl.PLAY_PAUSE -> RemoteCommand.FocusLive
                FocusedControl.LIVE -> RemoteCommand.FocusLive
            }
            KeyEvent.KEYCODE_DPAD_UP -> if (!overlayVisible) {
                RemoteCommand.OpenMiniEpg
            } else {
                when (focusedControl) {
                    FocusedControl.TIMELINE -> RemoteCommand.OpenMiniEpg
                    FocusedControl.PLAY_PAUSE,
                    FocusedControl.LIVE,
                    -> RemoteCommand.FocusTimeline
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (!overlayVisible) {
                RemoteCommand.OpenMiniEpg
            } else {
                when (focusedControl) {
                    FocusedControl.TIMELINE -> RemoteCommand.FocusPlayPause
                    FocusedControl.PLAY_PAUSE,
                    FocusedControl.LIVE,
                    -> RemoteCommand.FocusLive
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> when {
                !overlayVisible -> RemoteCommand.ShowOverlay
                focusedControl == FocusedControl.TIMELINE -> RemoteCommand.HideOverlay
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
}

private fun Int.toNumericDigit(): Int? = when (this) {
    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> this - KeyEvent.KEYCODE_0
    in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> this - KeyEvent.KEYCODE_NUMPAD_0
    else -> null
}
