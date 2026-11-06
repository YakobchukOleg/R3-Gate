package com.r3gate.app.backup
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class EveningBackupTest {
    @Test fun schedulesTonightInWarsaw() { assertEquals(3600000L,EveningBackup.delay(Instant.parse("2026-11-06T19:00:00Z"),21)) }
    @Test fun afterTheHourSchedulesTomorrow() { assertEquals(23*3600000L,EveningBackup.delay(Instant.parse("2026-11-06T21:00:00Z"),21)) }
    @Test fun springClockChangeUsesCalendarEvening() { assertEquals(23*3600000L,EveningBackup.delay(Instant.parse("2026-03-28T20:00:00Z"),21)) }
    @Test fun rejectsInvalidHour() { assertThrows(IllegalArgumentException::class.java) { EveningBackup.delay(Instant.now(),25) } }
}
