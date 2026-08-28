package sk.ziacik.androidtvplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerOverlayTimelineLabelTest {
    @Test
    fun `remaining label rounds partial minutes up`() {
        assertEquals("18 min left", formatRemainingTimeLabel(1_000L, 1_081_000L))
    }

    @Test
    fun `remaining label reaches zero at programme end`() {
        assertEquals("0 min left", formatRemainingTimeLabel(100_000L, 100_000L))
    }

    @Test
    fun `remaining label needs both programme times`() {
        assertNull(formatRemainingTimeLabel(null, 100_000L))
        assertNull(formatRemainingTimeLabel(50_000L, null))
    }
}
