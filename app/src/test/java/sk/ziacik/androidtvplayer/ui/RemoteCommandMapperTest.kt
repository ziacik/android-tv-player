package sk.ziacik.androidtvplayer.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCommandMapperTest {
    private val mapper = RemoteCommandMapper()

    @Test
    fun `numeric keys map to their decimal digit`() {
        (0..9).forEach { digit ->
            assertEquals(
                RemoteCommand.NumericDigit(digit),
                mapper.map(KeyEvent.KEYCODE_0 + digit, false, FocusedControl.PLAY_PAUSE),
            )
            assertEquals(
                RemoteCommand.NumericDigit(digit),
                mapper.map(KeyEvent.KEYCODE_NUMPAD_0 + digit, true, FocusedControl.LIVE),
            )
        }
    }

    @Test
    fun `channel keys switch independently of overlay visibility and focus`() {
        assertEquals(
            RemoteCommand.ChannelUp,
            mapper.map(KeyEvent.KEYCODE_CHANNEL_UP, false, FocusedControl.PLAY_PAUSE),
        )
        assertEquals(
            RemoteCommand.ChannelUp,
            mapper.map(KeyEvent.KEYCODE_CHANNEL_UP, true, FocusedControl.LIVE),
        )
        assertEquals(
            RemoteCommand.ChannelDown,
            mapper.map(KeyEvent.KEYCODE_CHANNEL_DOWN, false, FocusedControl.LIVE),
        )
        assertEquals(
            RemoteCommand.ChannelDown,
            mapper.map(KeyEvent.KEYCODE_CHANNEL_DOWN, true, FocusedControl.PLAY_PAUSE),
        )
    }

    @Test
    fun `page keys from DQ10 switch channels`() {
        assertEquals(
            RemoteCommand.ChannelUp,
            mapper.map(KeyEvent.KEYCODE_PAGE_UP, false, FocusedControl.PLAY_PAUSE),
        )
        assertEquals(
            RemoteCommand.ChannelDown,
            mapper.map(KeyEvent.KEYCODE_PAGE_DOWN, true, FocusedControl.LIVE),
        )
    }

    @Test
    fun `only channel selection commands are enabled while credentials are required`() {
        assertTrue(RemoteCommand.ChannelUp.isChannelSelection())
        assertTrue(RemoteCommand.ChannelDown.isChannelSelection())
        assertTrue(RemoteCommand.NumericDigit(2).isChannelSelection())
        assertFalse(RemoteCommand.TogglePlayback.isChannelSelection())
        assertFalse(RemoteCommand.Exit.isChannelSelection())
    }

    @Test
    fun `timeline uses left and right to seek while controls navigate horizontally`() {
        assertEquals(
            RemoteCommand.SeekBack,
            mapper.map(KeyEvent.KEYCODE_DPAD_LEFT, true, FocusedControl.TIMELINE),
        )
        assertEquals(
            RemoteCommand.SeekForward,
            mapper.map(KeyEvent.KEYCODE_DPAD_RIGHT, true, FocusedControl.TIMELINE),
        )
        assertEquals(
            RemoteCommand.FocusLive,
            mapper.map(KeyEvent.KEYCODE_DPAD_RIGHT, true, FocusedControl.PLAY_PAUSE),
        )
        assertEquals(
            RemoteCommand.FocusPlayPause,
            mapper.map(KeyEvent.KEYCODE_DPAD_LEFT, true, FocusedControl.LIVE),
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
            RemoteCommand.HideOverlay,
            mapper.map(KeyEvent.KEYCODE_DPAD_CENTER, true, FocusedControl.TIMELINE),
        )
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
    fun `up and down move between the timeline and playback controls`() {
        assertEquals(
            RemoteCommand.FocusTimeline,
            mapper.map(KeyEvent.KEYCODE_DPAD_UP, true, FocusedControl.LIVE),
        )
        assertEquals(
            RemoteCommand.FocusPlayPause,
            mapper.map(KeyEvent.KEYCODE_DPAD_DOWN, true, FocusedControl.TIMELINE),
        )
        assertEquals(
            RemoteCommand.FocusTimeline,
            mapper.map(KeyEvent.KEYCODE_DPAD_UP, true, FocusedControl.PLAY_PAUSE),
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

    @Test
    fun `hidden OSD command uses one minute while ordinary commands use six seconds`() {
        assertEquals(OverlayController.OK_TIMEOUT_MS, RemoteCommand.ShowOverlay.overlayTimeoutMs())
        assertEquals(OverlayController.NORMAL_TIMEOUT_MS, RemoteCommand.ChannelUp.overlayTimeoutMs())
        assertEquals(OverlayController.NORMAL_TIMEOUT_MS, RemoteCommand.SeekBack.overlayTimeoutMs())
        assertEquals(OverlayController.NORMAL_TIMEOUT_MS, RemoteCommand.TogglePlayback.overlayTimeoutMs())
        assertEquals(OverlayController.NORMAL_TIMEOUT_MS, RemoteCommand.FocusLive.overlayTimeoutMs())
        assertNull(RemoteCommand.HideOverlay.overlayTimeoutMs())
        assertNull(RemoteCommand.NumericDigit(1).overlayTimeoutMs())
    }
}
