package sk.ziacik.androidtvplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamInfoLabelTest {
    @Test
    fun `formats resolution and bitrate`() {
        assertEquals(
            "1920×1080 · 5.2 Mbps",
            formatStreamInfoLabel(width = 1920, height = 1080, bitrate = 5_200_000),
        )
    }

    @Test
    fun `falls back to resolution when bitrate is unknown`() {
        assertEquals(
            "1280×720",
            formatStreamInfoLabel(width = 1280, height = 720, bitrate = null),
        )
    }

    @Test
    fun `hides label when video format is unknown`() {
        assertNull(formatStreamInfoLabel(width = null, height = null, bitrate = null))
    }
}
