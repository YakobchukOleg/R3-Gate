package com.r3gate.app

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WorkPlaceRulesTest {
    @Test fun existingObjectsAndShopTagsRemainReadable() {
        assertEquals(WorkPlaceRules.Type.WORKSITE, WorkPlaceRules.type(null))
        assertEquals(WorkPlaceRules.Type.SHOP, WorkPlaceRules.type(null, legacyShop = true))
        assertEquals(WorkPlaceRules.Type.OFFICE, WorkPlaceRules.type("OFFICE", legacyShop = true))
        assertEquals("Biuro", WorkPlaceRules.workStage(WorkPlaceRules.Type.OFFICE))
        assertEquals("Obiekt", WorkPlaceRules.workStage(WorkPlaceRules.Type.SHOP))
    }

    @Test fun departureFromOfficeCanBeProposedButOnlyForThatOffice() {
        val shift = CoreGpsStageRules.Shift(true, "Biuro", "office")
        val type = CoreGpsProposalInbox.Kind.DEPARTURE
        assertEquals(CoreGpsStageRules.Proposal.START_TRAVEL,
            CoreGpsStageRules.proposal(type, "office", shift, setOf("office")))
        assertNull(CoreGpsStageRules.proposal(type, "other", shift, setOf("office", "other")))
        assertTrue(CoreGpsSessionGate.isEligible(CoreGpsSessionGate.Snapshot(
            true, true, "Biuro", 1_000L, 2_000L, 3_000L)))
    }

    @Test fun officeCountsAsWorkButNotAsWorksiteOrPrivateTime() {
        val day = LocalDate.of(2026, 11, 4)
        val zone = ZoneId.of("Europe/Warsaw")
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val hour = 3_600_000L
        val shift = TimeAnalytics.Shift(start, start + 2 * hour, listOf(
            TimeAnalytics.Stage(start, start + hour, TimeAnalytics.OFFICE),
            TimeAnalytics.Stage(start + hour, start + 2 * hour, TimeAnalytics.PERSONAL)))
        val result = TimeAnalytics.calculate(listOf(shift), day, day, zone)
        assertEquals(hour, result.workMillis)
        assertEquals(hour, result.personalMillis)
        assertEquals(hour, result.totals[TimeAnalytics.OFFICE])
        assertEquals(hour, result.hours[0][TimeAnalytics.OFFICE])
        assertEquals(0L, result.totals[0])
    }
}
