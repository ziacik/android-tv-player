package sk.ziacik.androidtvplayer.resolver

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkizaCredentialsProviderTest {
    @Test
    fun `uses Freeview default account when no local account exists`() {
        assertEquals(
            FreeviewMarkizaCredentials.default,
            MarkizaCredentialsProvider { null }.load(),
        )
    }

    @Test
    fun `uses locally saved account over Freeview default`() {
        val saved = MarkizaCredentials("own@example.com", "own-password")

        assertEquals(saved, MarkizaCredentialsProvider { saved }.load())
    }
}
