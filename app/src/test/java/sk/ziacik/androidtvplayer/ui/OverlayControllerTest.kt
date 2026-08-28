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
    fun `hides six seconds after normal show`() = runTest {
        val controller = OverlayController(this)

        controller.show(OverlayController.NORMAL_TIMEOUT_MS)
        assertTrue(controller.visible.value)

        advanceTimeBy(5_999L)
        runCurrent()
        assertTrue(controller.visible.value)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(controller.visible.value)
    }

    @Test
    fun `hides one minute after hidden OSD is opened with OK`() = runTest {
        val controller = OverlayController(this)

        controller.show(OverlayController.OK_TIMEOUT_MS)
        advanceTimeBy(59_999L)
        runCurrent()
        assertTrue(controller.visible.value)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(controller.visible.value)
    }

    @Test
    fun `ordinary action replaces a one minute timeout with six seconds`() = runTest {
        val controller = OverlayController(this)

        controller.show(OverlayController.OK_TIMEOUT_MS)
        advanceTimeBy(20_000L)
        controller.show(OverlayController.NORMAL_TIMEOUT_MS)
        advanceTimeBy(5_999L)
        runCurrent()

        assertTrue(controller.visible.value)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(controller.visible.value)
    }

    @Test
    fun `channel switch timeout starts only after programme title is ready`() = runTest {
        val controller = OverlayController(this)

        controller.showUntilProgramTitleReady()
        advanceTimeBy(30_000L)
        runCurrent()
        assertTrue(controller.visible.value)

        controller.onProgramTitleReady()
        advanceTimeBy(5_999L)
        runCurrent()
        assertTrue(controller.visible.value)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(controller.visible.value)
    }

    @Test
    fun `programme title ready does not shorten a normal overlay timeout`() = runTest {
        val controller = OverlayController(this)

        controller.show(OverlayController.OK_TIMEOUT_MS)
        advanceTimeBy(10_000L)
        controller.onProgramTitleReady()
        advanceTimeBy(49_999L)
        runCurrent()
        assertTrue(controller.visible.value)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(controller.visible.value)
    }
}
