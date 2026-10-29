package com.r3gate.app
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class HistoryRulesTest {
    private val zone=ZoneId.of("Europe/Warsaw")
    private fun ms(value:String)=LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    @Test fun midnightShiftSplitsIntoActualWorkDays() {
        val result=HistoryRules.slices(ms("2026-10-28T22:00"),ms("2026-10-29T02:00"),zone)
        assertEquals(listOf("2026-10-28","2026-10-29"),result.map { it.day })
        assertEquals(listOf(7200000L,7200000L),result.map { it.end-it.start })
        val selected=result.filter { DomainRules.inRange(it.day,"2026-10-29","2026-10-29") }
        assertEquals(7200000L,selected.sumOf { it.end-it.start })
    }
    @Test fun daylightSavingDayKeepsActualElapsedHours() {
        val result=HistoryRules.slices(ms("2026-10-25T00:00"),ms("2026-10-26T00:00"),zone)
        assertEquals(1,result.size);assertEquals(25*3600000L,result.single().end-result.single().start)
    }
    @Test fun zeroLengthRecordsRemainVisibleForEditing() {
        assertEquals(1,HistoryRules.slices(ms("2026-10-28T08:00"),ms("2026-10-28T08:00"),zone).size)
    }
}
