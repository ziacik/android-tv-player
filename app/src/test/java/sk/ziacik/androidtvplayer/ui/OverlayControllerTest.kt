package sk.ziacik.androidtvplayer.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayControllerTest {
    @Test
    fun `hides four seconds after show`() = runTest {
        val controller = OverlayController(this)

        controller.show()
        assertTrue(controller.visible.value)

        advanceTimeBy(3_999L)
        runCurrent()
        assertTrue(controller.visible.value)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(controller.visible.value)
    }

    @Test
    fun `interaction restarts full timeout`() = runTest {
        val controller = OverlayController(this)

        controller.show()
        advanceTimeBy(3_000L)
        controller.show()
        advanceTimeBy(3_999L)
        runCurrent()

        assertTrue(controller.visible.value)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(controller.visible.value)
    }
}

