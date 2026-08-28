package sk.ziacik.androidtvplayer.systemepg

import org.junit.Assert.assertEquals
import org.junit.Test

class TvInputPlaybackPolicyTest {
    @Test
    fun `allows a slower source and retries a failed stream load`() {
        assertEquals(25_000, TvInputPlaybackPolicy.connectTimeoutMs)
        assertEquals(30_000, TvInputPlaybackPolicy.readTimeoutMs)
        assertEquals(2, TvInputPlaybackPolicy.loadRetryCount)
    }
}
