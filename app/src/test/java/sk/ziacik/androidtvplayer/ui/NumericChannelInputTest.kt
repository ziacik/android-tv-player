package sk.ziacik.androidtvplayer.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidtvplayer.channel.TvChannel

@OptIn(ExperimentalCoroutinesApi::class)
class NumericChannelInputTest {
    @Test
    fun `two digits select one based catalogue channel after the final timeout`() = runTest {
        val selected = mutableListOf<TvChannel>()
        val input = NumericChannelInput(this, 1_000L, selected::add)

        input.append(1)
        advanceTimeBy(999L)
        input.append(2)
        advanceTimeBy(999L)
        runCurrent()
        assertEquals("12", input.digits.value)
        assertTrue(selected.isEmpty())

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(null, input.digits.value)
        assertEquals(listOf(TvChannel.entries[11]), selected)
    }

    @Test
    fun `invalid numbers and disposal do not select a channel`() = runTest {
        val selected = mutableListOf<TvChannel>()
        val input = NumericChannelInput(this, 1_000L, selected::add)

        input.append(0)
        advanceTimeBy(1_000L)
        runCurrent()
        input.append(9)
        input.append(9)
        advanceTimeBy(1_000L)
        runCurrent()
        input.append(1)
        input.cancel()
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(null, input.digits.value)
        assertTrue(selected.isEmpty())
    }
}
