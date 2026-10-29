package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.math.BigDecimal

class Update23Test {
    @Test fun calendarPresets() {
        val today=LocalDate.of(2026,10,29)
        assertEquals(LocalDate.of(2026,10,19) to LocalDate.of(2026,10,25),TimeAnalytics.previousWeek(today))
        assertEquals(LocalDate.of(2026,9,1) to LocalDate.of(2026,9,30),TimeAnalytics.previousMonth(today))
    }
    @Test fun overlapsAndGapsDoNotLoseOrDuplicateTime() {
        val zone=ZoneId.of("Europe/Warsaw");val day=LocalDate.of(2026,10,29); val start=day.atTime(8,0).atZone(zone).toInstant().toEpochMilli()
        val hour=3600000L
        val result=TimeAnalytics.calculate(listOf(TimeAnalytics.Shift(start,start+4*hour,listOf(TimeAnalytics.Stage(start,start+2*hour,0),TimeAnalytics.Stage(start+hour,start+3*hour,2)))),day,day,zone)
        assertEquals(4*hour,result.totals.sum());assertEquals(hour,result.totals[0]);assertEquals(2*hour,result.totals[2]);assertEquals(hour,result.totals[3]);assertEquals(result.totals.sum(),result.hours.sumOf { it.sum() })
    }
    @Test fun daylightSavingHourAndMidnightFiltering() {
        val zone=ZoneId.of("Europe/Warsaw");val day=LocalDate.of(2026,10,25)
        val start=day.atStartOfDay(zone).toInstant().toEpochMilli(); val end=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val result=TimeAnalytics.calculate(listOf(TimeAnalytics.Shift(start-3600000,end+3600000,emptyList())),day,day,zone)
        assertEquals(25*3600000L,result.totals.sum()); assertEquals(2*3600000L,result.hours[2][3])
    }
    @Test fun stockTransfersEditsAndDeletionAreDerived() {
        val source=MaterialStock.Entry("a","Kabel","m",BigDecimal("10"),true)
        val transfer=MaterialStock.Entry("b","Kabel","m",BigDecimal("4.5"),false,"a")
        assertEquals(BigDecimal("5.5"),MaterialStock.remaining(source,listOf(source,transfer)))
        assertTrue(MaterialStock.valid(listOf(source,transfer)))
        assertFalse(MaterialStock.valid(listOf(source,transfer.copy(quantity=BigDecimal("11")))))
        assertFalse(MaterialStock.valid(listOf(transfer)))
        assertEquals(BigDecimal("10"),MaterialStock.remaining(source,listOf(source)))
    }
}
