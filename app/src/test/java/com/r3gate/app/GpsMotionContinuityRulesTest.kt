package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsMotionContinuityRulesTest {
    @Test fun recentSequentialFixesAllowMovementEvaluation() {
        assertTrue(GpsMotionContinuityRules.contiguous(1000,61000))
        assertTrue(GpsMotionContinuityRules.contiguous(1000,1000+GpsTripRules.MAX_EVIDENCE_GAP_MS))
    }
    @Test fun lostSignalAndOutOfOrderFixesCannotAdvanceTripState() {
        assertFalse(GpsMotionContinuityRules.contiguous(1000,1001+GpsTripRules.MAX_EVIDENCE_GAP_MS))
        assertFalse(GpsMotionContinuityRules.contiguous(1000,1000))
        assertFalse(GpsMotionContinuityRules.contiguous(1000,999))
        assertFalse(GpsMotionContinuityRules.contiguous(0,1000))
    }
}
