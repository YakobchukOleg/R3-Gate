package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsSessionBoundaryRulesTest {
    @Test fun firstLaunchDoesNotInventCoverageGap() {
        assertFalse(GpsSessionBoundaryRules.shouldRecordStartupGap(0L,1000L))
    }
    @Test fun missingTimestampDoesNotCreateGap() {
        assertFalse(GpsSessionBoundaryRules.shouldRecordStartupGap(-1L,1000L))
    }
    @Test fun restartWithPreviousFixRecordsBoundary() {
        assertTrue(GpsSessionBoundaryRules.shouldRecordStartupGap(1000L,2000L))
    }
    @Test fun staleOrEqualClockDoesNotRecordBoundary() {
        assertFalse(GpsSessionBoundaryRules.shouldRecordStartupGap(1000L,1000L))
    }
    @Test fun backwardsClockDoesNotRecordBoundary() {
        assertFalse(GpsSessionBoundaryRules.shouldRecordStartupGap(2000L,1000L))
    }
    @Test fun shutdownWithoutStartedSessionHasNoBoundary() {
        assertFalse(GpsSessionBoundaryRules.shouldRecordShutdownGap(false))
    }
    @Test fun shutdownAfterStartedSessionHasBoundary() {
        assertTrue(GpsSessionBoundaryRules.shouldRecordShutdownGap(true))
    }
}
