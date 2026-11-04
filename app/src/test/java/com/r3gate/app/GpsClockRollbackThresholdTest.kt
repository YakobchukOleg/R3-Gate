package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsClockRollbackThresholdTest {
    private val threshold=GpsAdaptiveSampling.CLOCK_ROLLBACK_RESET_MS
    @Test fun adaptiveClockRollbackOneMsDoesNotReset() {
        val state=GpsAdaptiveSampling.State(startedMs=100_000L,lastMotionMs=100_000L)
        assertEquals(state,GpsAdaptiveSampling.observe(state,99_999L,2.0))
    }
    @Test fun adaptiveRollbackBelowThresholdRejectsOutOfOrder() {
        val state=GpsAdaptiveSampling.State(startedMs=100_000L,lastMotionMs=100_000L)
        assertEquals(state,GpsAdaptiveSampling.observe(state,100_000L-threshold+1,2.0))
    }
    @Test fun adaptiveRollbackAtThresholdStartsFreshEpoch() {
        val state=GpsAdaptiveSampling.State(startedMs=100_000L,lastMotionMs=100_000L)
        val next=GpsAdaptiveSampling.observe(state,100_000L-threshold,2.0)
        assertEquals(100_000L-threshold,next.startedMs)
    }
    @Test fun adaptiveGpsTravelBelowThresholdRejectsLateEvidence() {
        val state=GpsAdaptiveSampling.State(startedMs=100_000L,lastGpsTravelMs=100_000L)
        assertEquals(state,GpsAdaptiveSampling.gpsTravel(state,100_000L-threshold+1))
    }
    @Test fun adaptiveGpsTravelAtThresholdReanchors() {
        val state=GpsAdaptiveSampling.State(startedMs=100_000L,lastGpsTravelMs=100_000L)
        val next=GpsAdaptiveSampling.gpsTravel(state,100_000L-threshold)
        assertEquals(100_000L-threshold,next.lastGpsTravelMs)
    }
    @Test fun motionWakeSmallOutOfOrderSampleIgnored() {
        val state=GpsMotionWakeRules.State(sinceMs=100_000L,lastSampleMs=101_000L)
        assertEquals(state,GpsMotionWakeRules.observe(state,100_999L,3.0).state)
    }
    @Test fun motionWakeBelowRollbackThresholdIgnored() {
        val state=GpsMotionWakeRules.State(sinceMs=100_000L,lastSampleMs=101_000L)
        assertEquals(state,GpsMotionWakeRules.observe(state,101_000L-threshold+1,3.0).state)
    }
    @Test fun motionWakeAtRollbackThresholdStartsNewCandidate() {
        val state=GpsMotionWakeRules.State(sinceMs=100_000L,lastSampleMs=101_000L)
        val now=101_000L-threshold
        assertEquals(now,GpsMotionWakeRules.observe(state,now,3.0).state.sinceMs)
    }
    @Test fun rollbackAtThresholdNeverTriggersImmediateMotionWake() {
        val state=GpsMotionWakeRules.State(sinceMs=100_000L,lastSampleMs=101_000L)
        assertFalse(GpsMotionWakeRules.observe(state,101_000L-threshold,3.0).wake)
    }
    @Test fun quietObservationAfterRollbackHasNoCandidate() {
        val state=GpsMotionWakeRules.State(sinceMs=100_000L,lastSampleMs=101_000L)
        assertEquals(0L,GpsMotionWakeRules.observe(state,101_000L-threshold,0.1).state.sinceMs)
    }
}
