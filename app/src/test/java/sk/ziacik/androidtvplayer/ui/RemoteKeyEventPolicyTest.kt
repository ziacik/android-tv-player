package sk.ziacik.androidtvplayer.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteKeyEventPolicyTest {
    @Test
    fun `mini EPG vertical navigation is handled on key down so Android repeats are preserved`() {
        assertTrue(
            shouldHandleRemoteKeyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                miniEpgVisible = true,
            ),
        )
        assertTrue(
            shouldHandleRemoteKeyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                miniEpgVisible = true,
            ),
        )
        assertFalse(
            shouldHandleRemoteKeyEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                miniEpgVisible = true,
            ),
        )
    }

    @Test
    fun `timeline horizontal keys preserve key down repeats and key up commit`() {
        assertTrue(
            shouldHandleRemoteKeyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                miniEpgVisible = false,
                overlayVisible = true,
                focusedControl = FocusedControl.TIMELINE,
            ),
        )
        assertTrue(
            shouldHandleRemoteKeyEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                miniEpgVisible = false,
                overlayVisible = true,
                focusedControl = FocusedControl.TIMELINE,
            ),
        )
        assertFalse(
            shouldHandleRemoteKeyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                miniEpgVisible = false,
                overlayVisible = true,
                focusedControl = FocusedControl.PLAY_PAUSE,
            ),
        )
        assertTrue(
            shouldHandleRemoteKeyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                miniEpgVisible = false,
                overlayVisible = false,
                focusedControl = FocusedControl.PLAY_PAUSE,
            ),
        )
    }

    @Test
    fun `scrub step accelerates with remote key repeats`() {
        assertEquals(10_000L, seekScrubStepMs(0))
        assertEquals(10_000L, seekScrubStepMs(5))
        assertEquals(30_000L, seekScrubStepMs(6))
        assertEquals(60_000L, seekScrubStepMs(20))
        assertEquals(300_000L, seekScrubStepMs(50))
    }

    @Test
    fun `other remote commands keep their existing key up behavior`() {
        assertFalse(
            shouldHandleRemoteKeyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                miniEpgVisible = true,
            ),
        )
        assertTrue(
            shouldHandleRemoteKeyEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                miniEpgVisible = true,
            ),
        )
        assertFalse(
            shouldHandleRemoteKeyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                miniEpgVisible = false,
            ),
        )
        assertTrue(
            shouldHandleRemoteKeyEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                miniEpgVisible = false,
            ),
        )
    }
}
