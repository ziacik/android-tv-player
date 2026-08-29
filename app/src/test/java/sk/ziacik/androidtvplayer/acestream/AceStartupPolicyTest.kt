package sk.ziacik.androidtvplayer.acestream

import org.junit.Assert.assertEquals
import org.junit.Test

class AceStartupPolicyTest {
    @Test
    fun `waits for both local AceServe ports`() {
        assertEquals(listOf(6878, 62062), AceStartupPolicy.REQUIRED_PORTS)
    }

    @Test
    fun `allows slow Android TV startup`() {
        assertEquals(90_000L, AceStartupPolicy.STARTUP_TIMEOUT_MILLIS)
    }
}
