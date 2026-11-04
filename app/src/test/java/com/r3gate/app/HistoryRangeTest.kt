package com.r3gate.app

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HistoryRangeTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val day = LocalDate.of(2026, 11, 4)
    private val midnight = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    @Test fun shiftStartingThePreviousDayStillAppearsInSelectedDay() {
        val result = HistoryRules.slicesInRange(midnight - hour, midnight + hour, day, day, zone)
        assertEquals(listOf(HistoryRules.Slice(day.toString(), midnight, midnight + hour)), result)
    }

    @Test fun dateRangeClipsAtBothLocalMidnights() {
        val result = HistoryRules.slicesInRange(midnight - hour, midnight + 25 * hour, day, day, zone)
        assertEquals(listOf(HistoryRules.Slice(day.toString(), midnight, midnight + 24 * hour)), result)
    }

    @Test fun reversedRangeAndInvalidShiftDoNotProduceReportLines() {
        assertTrue(HistoryRules.slicesInRange(midnight, midnight + hour, day, day.minusDays(1), zone).isEmpty())
        assertTrue(HistoryRules.slicesInRange(-1L, midnight, day, day, zone).isEmpty())
    }
}
