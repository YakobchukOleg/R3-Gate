package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsTraceRulesTest {
    private val rules=GpsTraceRules

    @Test fun firstFixGetsLogged() { assertTrue(rules.shouldLog("FIX",1000L,0L)) }
    @Test fun rapidSecondFixIsThrottled() { assertFalse(rules.shouldLog("FIX",1001L,1000L)) }
    @Test fun fixAtIntervalGetsLogged() {
        assertTrue(rules.shouldLog("FIX",1000L+rules.FIX_INTERVAL_MS,1000L))
    }
    @Test fun fixJustBeforeIntervalGetsThrottled() {
        assertFalse(rules.shouldLog("FIX",1000L+rules.FIX_INTERVAL_MS-1L,1000L))
    }
    @Test fun geofenceEvidenceThrottledBetweenSamples() {
        assertFalse(rules.shouldLog("ZONE",2000L,1000L))
    }
    @Test fun tripEvidenceThrottledBetweenSamples() {
        assertFalse(rules.shouldLog("TRIP_EVIDENCE",2000L,1000L))
    }
    @Test fun rejectionReasonsAreThrottled() {
        for(kind in listOf("REJECT_QUALITY","REJECT_ORDER","REJECT_JUMP","REJECT_SOURCE"))
            assertFalse(rules.shouldLog(kind,2000L,1000L))
    }
    @Test fun rejectionIsRecordedAtSamplingBoundary() {
        assertTrue(rules.shouldLog("REJECT_JUMP",1000L+rules.REJECTION_INTERVAL_MS,1000L))
    }
    @Test fun departureAlwaysLogged() {
        assertTrue(rules.shouldLog("DEPARTURE",1001L,1000L))
    }
    @Test fun tripStartAlwaysLogged() {
        assertTrue(rules.shouldLog("TRIP_START",1001L,1000L))
    }
    @Test fun serviceStopAlwaysLogged() {
        assertTrue(rules.shouldLog("SERVICE_STOP",1001L,1000L))
    }
    @Test fun repeatedCrashBoundaryIsStillRecorded() {
        assertTrue(rules.shouldLog("GPS_GAP",1001L,1000L))
    }
    @Test fun zeroTimestampNeverLogged() {
        assertFalse(rules.shouldLog("DEPARTURE",0L,0L))
    }
    @Test fun negativeTimestampNeverLogged() {
        assertFalse(rules.shouldLog("TRIP_STOP",-1L,0L))
    }
    @Test fun clockRollbackCanBeLoggedForFix() {
        assertTrue(rules.shouldLog("FIX",1000L,1200L))
    }
    @Test fun emptyJournalKeepsFirstEntry() {
        assertEquals(0,rules.retainedStart(1))
    }
    @Test fun exactJournalLimitDoesNotTrim() {
        assertEquals(0,rules.retainedStart(rules.MAX_ENTRIES))
    }
    @Test fun journalBeyondLimitDropsOldest() {
        assertEquals(42,rules.retainedStart(rules.MAX_ENTRIES+42))
    }
    @Test fun tinyLimitKeepsNewestOnly() {
        assertEquals(4,rules.retainedStart(5,1))
    }
    @Test fun zeroLimitIsClampedToOne() {
        assertEquals(4,rules.retainedStart(5,0))
    }
}
