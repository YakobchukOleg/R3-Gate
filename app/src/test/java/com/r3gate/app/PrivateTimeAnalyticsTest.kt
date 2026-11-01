package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PrivateTimeAnalyticsTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    @Test fun chartDoesNotTreatPrivateAsUnassigned() {
        val first = LocalDate.of(2026, 10, 30).atStartOfDay(zone).toInstant().toEpochMilli()
        val hour = 3_600_000L
        val shift = TimeAnalytics.Shift(first, first+3*hour,listOf(
            TimeAnalytics.Stage(first,first+hour,0),
            TimeAnalytics.Stage(first+hour,first+2*hour,-1),
            TimeAnalytics.Stage(first+2*hour,first+3*hour,2)
        ))
        val result = TimeAnalytics.calculate(listOf(shift),LocalDate.of(2026,10,30),LocalDate.of(2026,10,30),zone)
        assertEquals(2*hour,result.totals.sum())
        assertEquals(hour,result.totals[0])
        assertEquals(hour,result.totals[2])
        assertEquals(0L,result.totals[3])
    }
}
