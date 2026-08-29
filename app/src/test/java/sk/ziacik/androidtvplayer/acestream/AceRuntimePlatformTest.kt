package sk.ziacik.androidtvplayer.acestream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AceRuntimePlatformTest {
    @Test
    fun `selects arm64 for a 64 bit process`() {
        assertEquals(
            "arm64-v8a",
            AceRuntimePlatform.selectAbi(
                supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
                is64Bit = true,
            ),
        )
    }

    @Test
    fun `selects armv7 for a 32 bit process`() {
        assertEquals(
            "armeabi-v7a",
            AceRuntimePlatform.selectAbi(
                supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
                is64Bit = false,
            ),
        )
    }

    @Test
    fun `returns null when process ABI is unavailable`() {
        assertNull(
            AceRuntimePlatform.selectAbi(
                supportedAbis = listOf("armeabi-v7a"),
                is64Bit = true,
            ),
        )
    }

    @Test
    fun `detects acestream source`() {
        assertTrue(AceRuntimePlatform.isAceSource("acestream://abc"))
        assertFalse(AceRuntimePlatform.isAceSource("https://example.test/live.m3u8"))
        assertFalse(AceRuntimePlatform.isAceSource(null))
    }
}
