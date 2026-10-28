package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DomainRulesTest {
    @Test fun sequentialTagsDoNotReuseDeletedOrArchivedNumbers() {
        assertEquals("KOP-04",DomainRules.nextTag("Kopytkowo",3,listOf("KOP-01","KOP-03")).first)
        assertEquals("KOP-11",DomainRules.nextTag("Kopytkowo",0,listOf("KOP-10")).first)
        assertEquals("KOP-02",DomainRules.nextTag("Kopanka",0,listOf("KOP-01")).first)
    }
    @Test fun cityFormattingHandlesPolishLettersAndWhitespace() {
        assertEquals("LO D".replace(" ",""),DomainRules.cityPrefix(" Łódź "))
        assertEquals("GDA",DomainRules.cityPrefix("Gdańsk"))
        assertEquals("KOP-01",DomainRules.nextTag("kopytkowo",0,emptyList()).first)
    }
    @Test fun materialQuantitiesRespectUnits() {
        assertEquals("12.5",DomainRules.quantity("12,50","m")!!.toPlainString())
        assertEquals("2",DomainRules.quantity("2.0","szt.")!!.toPlainString())
        assertNull(DomainRules.quantity("1.5","szt."))
        assertNull(DomainRules.quantity("-2","m"))
        assertNull(DomainRules.quantity("0","m"))
        assertNull(DomainRules.quantity("text","m"))
        assertNull(DomainRules.quantity("5","kg"))
    }
    @Test fun filtersIncludeBothBoundaryDatesAndHandleLegacyUnknownDates() {
        assertTrue(DomainRules.inRange("2026-10-21","2026-10-21","2026-10-27"))
        assertTrue(DomainRules.inRange("2026-10-27","2026-10-21","2026-10-27"))
        assertFalse(DomainRules.inRange("2026-10-28","2026-10-21","2026-10-27"))
        assertFalse(DomainRules.inRange("","2026-10-21","2026-10-27"))
        assertTrue(DomainRules.inRange("","",""))
    }
    @Test fun notesCanContainOnlyPhotoButCannotBeEmpty() {
        assertTrue(DomainRules.validNote("",1))
        assertTrue(DomainRules.validNote("Zabrać kabel",0))
        assertFalse(DomainRules.validNote(" ",0))
    }
    @Test fun objectHoursSplitAtMidnight() {
        val zone=ZoneId.of("Europe/Warsaw")
        val a=ZonedDateTime.parse("2026-10-28T23:00:00+01:00[Europe/Warsaw]").toInstant().toEpochMilli()
        val b=ZonedDateTime.parse("2026-10-29T01:30:00+01:00[Europe/Warsaw]").toInstant().toEpochMilli()
        assertEquals(mapOf("2026-10-28" to 3600000L,"2026-10-29" to 5400000L),DomainRules.timeByDay(a,b,zone))
        assertTrue(DomainRules.timeByDay(b,a,zone).isEmpty())
    }
    @Test fun daylightSavingDayUsesActualElapsedTime() {
        val zone=ZoneId.of("Europe/Warsaw")
        val a=ZonedDateTime.parse("2026-10-25T00:00:00+02:00[Europe/Warsaw]").toInstant().toEpochMilli()
        val b=ZonedDateTime.parse("2026-10-26T00:00:00+01:00[Europe/Warsaw]").toInstant().toEpochMilli()
        assertEquals(25*3600000L,DomainRules.timeByDay(a,b,zone)["2026-10-25"])
    }
}
