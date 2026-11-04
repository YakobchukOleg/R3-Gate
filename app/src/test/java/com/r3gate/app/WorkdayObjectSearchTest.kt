package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class WorkdayObjectSearchTest {
    private val now = 100_000L
    private val fix = GpsWorkRules.Point(54.0, 18.0, 10.0, now)
    private val site = GpsWorkRules.Place("site", GpsWorkRules.Kind.OBJECT, 54.0, 18.0)

    @Test fun emptyOrWhitespaceSearchDoesNotRevealTheCatalog() {
        assertFalse(WorkdayObjectSearch.matchesCity("Tczew", "", true))
        assertFalse(WorkdayObjectSearch.matchesCity("Tczew", "  ", true))
        assertTrue(WorkdayObjectSearch.matchesCity("Tczew", "", false))
    }

    @Test fun citySearchIgnoresCaseAndPolishDiacritics() {
        assertTrue(WorkdayObjectSearch.matchesCity("Łódź", "  LODZ ", true))
        assertTrue(WorkdayObjectSearch.matchesCity("Tczew", "tcz", true))
        assertFalse(WorkdayObjectSearch.matchesCity("Gdańsk", "tcz", true))
    }

    @Test fun suggestsOnlyWhenAValidFixMatches() {
        assertEquals("site", WorkdayObjectSearch.suggestedId(fix, listOf(site), now))
        assertNull(WorkdayObjectSearch.suggestedId(null, listOf(site), now))
        assertNull(WorkdayObjectSearch.suggestedId(fix, emptyList(), now))
    }

    @Test fun staleAndImpreciseFixesFallBackToSearch() {
        assertNull(WorkdayObjectSearch.suggestedId(fix.copy(timestampMs = now - 30_001), listOf(site), now))
        assertNull(WorkdayObjectSearch.suggestedId(fix.copy(accuracyM = 110.0), listOf(site), now))
    }

    @Test fun outOfRangeAndAmbiguousPlacesFallBackToSearch() {
        assertNull(WorkdayObjectSearch.suggestedId(fix.copy(latitude = 54.01), listOf(site), now))
        assertNull(WorkdayObjectSearch.suggestedId(fix, listOf(site, site.copy(id = "second")), now))
    }
}
