package sk.ziacik.androidtvplayer.acestream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AceRuntimeCommandTest {
    @Test
    fun `builds minimal AceServe command`() {
        val command = AceRuntimeCommand.build(
            runner = "/native/libacepython.so",
            script = "/files/aceserve/main_android.py",
        )

        assertEquals("/native/libacepython.so", command.first())
        assertEquals("/files/aceserve/main_android.py", command[1])
        assertTrue(command.contains("--bind-all"))
        assertTrue(command.contains("--disable-sentry"))
        assertTrue(command.contains("--disable-upnp"))
        assertTrue(command.contains("--log-stdout"))
    }

    @Test
    fun `builds runtime python path`() {
        assertEquals(
            listOf(
                "/ace/python/lib/stdlib",
                "/ace/python/lib/modules",
                "/ace/data",
                "/ace/modules.zip",
                "/ace/eggs-unpacked",
                "/ace/lib",
            ).joinToString(":"),
            AceRuntimeCommand.pythonPath("/ace"),
        )
    }
}
