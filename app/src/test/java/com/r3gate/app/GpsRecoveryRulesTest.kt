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
        assertEquals(GpsRecoveryRules.Plan(true,true,false),GpsRecoveryRules.plan(0,20_000))
    }
    @Test fun edgeThresholdDoesNotCountAsGap() {
        assertFalse(GpsRecoveryRules.plan(1,1+GpsRecoveryRules.MAX_GAP_MS).recordGap)
    }
}
