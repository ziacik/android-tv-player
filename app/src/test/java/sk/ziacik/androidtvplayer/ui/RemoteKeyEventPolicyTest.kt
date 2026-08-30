package sk.ziacik.androidtvplayer.ui

import android.view.KeyEvent
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
