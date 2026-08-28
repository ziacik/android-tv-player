package sk.ziacik.androidtvplayer.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerOverlayTimelineVisibilityTest {
    @Test
    fun `program timeline keeps its layout slot even without EPG`() {
        assertTrue(shouldShowProgramTimeline(null))
        assertTrue(shouldShowProgramTimeline(0f))
    }
}
