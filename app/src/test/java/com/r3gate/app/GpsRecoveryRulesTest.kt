package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsRecoveryRulesTest {
    @Test fun normalFixDoesNotEraseEvidence() {
        assertEquals(GpsRecoveryRules.Plan(false,false,false),
            GpsRecoveryRules.plan(1_000,61_000))
    }
    @Test fun longGapResetsBothStatesAndRecordsMissingCoverage() {
        assertEquals(GpsRecoveryRules.Plan(true,true,true),
            GpsRecoveryRules.plan(1_000,1_000+GpsRecoveryRules.MAX_GAP_MS+1))
    }
    @Test fun duplicateOrOutOfOrderFixNeverResetsState() {
        assertEquals(GpsRecoveryRules.Plan(false,false,false),GpsRecoveryRules.plan(5_000,5_000))
        assertEquals(GpsRecoveryRules.Plan(false,false,false),GpsRecoveryRules.plan(5_000,4_000))
    }
    @Test fun freshProcessStartsWithCleanStatesWithoutFakeGap() {
        assertEquals(GpsRecoveryRules.Plan(false,true,false),GpsRecoveryRules.plan(0,20_000))
    }
    @Test fun edgeThresholdDoesNotCountAsGap() {
        assertFalse(GpsRecoveryRules.plan(1,1+GpsRecoveryRules.MAX_GAP_MS).recordGap)
    }

    @Test fun firstFixDoesNotResetConfirmedArrivalEvidence() {
        assertFalse(GpsRecoveryRules.plan(0L,1_000L).resetArrival)
    }
    @Test fun firstFixStillResetsMotionState() {
        assertTrue(GpsRecoveryRules.plan(0L,1_000L).resetMotion)
    }
    @Test fun firstFixNeverInventsCoverageGap() {
        assertFalse(GpsRecoveryRules.plan(0L,1_000L).recordGap)
    }
    @Test fun missingFixTimestampBehavesLikeNewSession() {
        assertEquals(GpsRecoveryRules.Plan(false,true,false),
            GpsRecoveryRules.plan(-1L,1_000L))
    }
    @Test fun actualLongOutageStillInvalidatesArrival() {
        assertTrue(GpsRecoveryRules.plan(1_000L,
            1_000L+GpsRecoveryRules.MAX_GAP_MS+1).resetArrival)
    }
    @Test fun actualLongOutageStillRecordsCoverageGap() {
        assertTrue(GpsRecoveryRules.plan(1_000L,
            1_000L+GpsRecoveryRules.MAX_GAP_MS+1).recordGap)
    }
}
