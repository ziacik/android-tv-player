package sk.ziacik.androidtvplayer.acestream

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AcePlaybackGateTest {
    @Test
    fun `starts engine for Ace source`() = runTest {
        var starts = 0
        val gate = AcePlaybackGate { starts += 1 }

        gate.prepare("acestream://abc")

        assertEquals(1, starts)
    }

    @Test
    fun `does not start engine for normal stream`() = runTest {
        var starts = 0
        val gate = AcePlaybackGate { starts += 1 }

        gate.prepare("https://example.test/live.m3u8")

        assertEquals(0, starts)
    }
}
