package sk.ziacik.androidtvplayer.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteCommandMapperTest {
    private val mapper = RemoteCommandMapper()

    @Test
    fun `left and right always seek directly`() {
        assertEquals(
            RemoteCommand.SeekBack,
            mapper.map(KeyEvent.KEYCODE_DPAD_LEFT, false, FocusedControl.PLAY_PAUSE),
        )
        assertEquals(
            RemoteCommand.SeekForward,
            mapper.map(KeyEvent.KEYCODE_DPAD_RIGHT, true, FocusedControl.LIVE),
        )
    }

    @Test
    fun `center shows a hidden overlay`() {
        assertEquals(
            RemoteCommand.ShowOverlay,
            mapper.map(KeyEvent.KEYCODE_DPAD_CENTER, false, FocusedControl.PLAY_PAUSE),
        )
    }

    @Test
    fun `center activates focused visible control`() {
        assertEquals(
            RemoteCommand.TogglePlayback,
            mapper.map(KeyEvent.KEYCODE_DPAD_CENTER, true, FocusedControl.PLAY_PAUSE),
        )
        assertEquals(
            RemoteCommand.GoLive,
            mapper.map(KeyEvent.KEYCODE_DPAD_CENTER, true, FocusedControl.LIVE),
        )
    }

    @Test
    fun `up and down select play pause and live`() {
        assertEquals(
            RemoteCommand.FocusPlayPause,
            mapper.map(KeyEvent.KEYCODE_DPAD_UP, true, FocusedControl.LIVE),
        )
        assertEquals(
            RemoteCommand.FocusLive,
            mapper.map(KeyEvent.KEYCODE_DPAD_DOWN, true, FocusedControl.PLAY_PAUSE),
        )
    }

    @Test
    fun `back hides overlay before exiting`() {
        assertEquals(
            RemoteCommand.HideOverlay,
            mapper.map(KeyEvent.KEYCODE_BACK, true, FocusedControl.PLAY_PAUSE),
        )
        assertEquals(
            RemoteCommand.Exit,
            mapper.map(KeyEvent.KEYCODE_BACK, false, FocusedControl.PLAY_PAUSE),
        )
    }

    @Test
    fun `unknown key is ignored`() {
        assertEquals(
            RemoteCommand.Ignore,
            mapper.map(KeyEvent.KEYCODE_VOLUME_UP, true, FocusedControl.PLAY_PAUSE),
        )
    }
}
