package sk.ziacik.androidtvplayer.systemepg

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemEpgSyncScheduleTest {
    @Test
    fun `refreshes the system guide every six hours`() {
        assertEquals(6L, SystemEpgSyncSchedule.periodHours)
    }
}
