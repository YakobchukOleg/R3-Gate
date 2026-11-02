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

    @Test fun clockRollbackIsDetected() {
        assertTrue(GpsSessionBoundaryRules.clockRolledBack(5000L,4000L))
    }
    @Test fun equalClockIsNotRollback() {
        assertFalse(GpsSessionBoundaryRules.clockRolledBack(5000L,5000L))
    }
    @Test fun forwardClockIsNotRollback() {
        assertFalse(GpsSessionBoundaryRules.clockRolledBack(4000L,5000L))
    }
    @Test fun emptyCheckpointIsNotRollback() {
        assertFalse(GpsSessionBoundaryRules.clockRolledBack(0L,5000L))
    }
    @Test fun invalidCurrentTimestampIsNotRollback() {
        assertFalse(GpsSessionBoundaryRules.clockRolledBack(5000L,0L))
    }
    @Test fun rollbackDoesNotGenerateStartupGap() {
        val previous=5000L
        val now=4000L
        assertTrue(GpsSessionBoundaryRules.clockRolledBack(previous,now))
        assertFalse(GpsSessionBoundaryRules.shouldRecordStartupGap(previous,now))
    }
}
