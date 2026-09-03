package sk.ziacik.androidtvplayer.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteMediaKeyTest {
    private val mapper = RemoteCommandMapper()

    @Test
    fun `remote media playback keys toggle playback regardless of overlay state`() {
        val mediaKeys = listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
        )

        mediaKeys.forEach { keyCode ->
            assertEquals(
                RemoteCommand.TogglePlayback,
                mapper.map(
                    keyCode = keyCode,
                    overlayVisible = false,
                    focusedControl = FocusedControl.TIMELINE,
                ),
            )
            assertEquals(
                RemoteCommand.TogglePlayback,
                mapper.map(
                    keyCode = keyCode,
                    overlayVisible = true,
                    focusedControl = FocusedControl.LIVE,
                    miniEpgVisible = true,
                ),
            )
        }
    }
}
