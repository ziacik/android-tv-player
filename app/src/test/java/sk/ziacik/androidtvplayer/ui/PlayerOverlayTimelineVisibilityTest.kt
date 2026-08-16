package sk.ziacik.androidtvplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerOverlayTimelineVisibilityTest {
    @Test
    fun `program timeline is hidden when no scheduled programme interval exists`() {
        assertFalse(shouldShowProgramTimeline(null))
        assertTrue(shouldShowProgramTimeline(0f))
    }
}
