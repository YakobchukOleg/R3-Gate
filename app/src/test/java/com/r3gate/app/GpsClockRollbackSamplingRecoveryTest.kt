package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsClockRollbackSamplingRecoveryTest {
    private val rules=GpsAdaptiveSampling
    @Test fun normalQuietIntervalRemainsQuiet() {
        val s=GpsAdaptiveSampling.State(startedMs=1_000L)
        assertEquals(GpsAdaptiveSampling.QUIET_INTERVAL_MS,
            rules.interval(s,500_000L,true))
    }
    @Test fun backwardClockCorrectionResetsSamplingEpoch() {
        val old=GpsAdaptiveSampling.State(startedMs=500_000L,lastMotionMs=510_000L)
        val next=rules.observe(old,200_000L,0.0)
        assertEquals(200_000L,next.startedMs)
        assertEquals(0L,next.lastMotionMs)
    }
    @Test fun rolloverRequiresNewQuietPeriod() {
        val old=GpsAdaptiveSampling.State(startedMs=500_000L,lastMotionMs=510_000L)
        val next=rules.observe(old,200_000L,0.0)
        assertEquals(GpsAdaptiveSampling.ACTIVE_INTERVAL_MS,
            rules.interval(next,201_000L,true))
    }
    @Test fun resetEpochEventuallyAllowsQuiet() {
        val next=rules.observe(GpsAdaptiveSampling.State(startedMs=500_000L),
            200_000L,0.0)
        assertEquals(GpsAdaptiveSampling.QUIET_INTERVAL_MS,
            rules.interval(next,500_000L,true))
    }
    @Test fun movementAfterRollbackIsRetained() {
        val old=GpsAdaptiveSampling.State(startedMs=500_000L,lastMotionMs=510_000L)
        val next=rules.observe(old,200_000L,2.0)
        assertEquals(200_000L,next.lastMotionMs)
    }
    @Test fun gpsTravelAfterRollbackReanchorsTime() {
        val old=GpsAdaptiveSampling.State(startedMs=500_000L,lastGpsTravelMs=510_000L)
        val next=rules.gpsTravel(old,200_000L)
        assertEquals(200_000L,next.startedMs)
        assertEquals(200_000L,next.lastGpsTravelMs)
    }
    @Test fun previousFutureGpsTravelTriggersReset() {
        val old=GpsAdaptiveSampling.State(startedMs=100_000L,lastGpsTravelMs=500_000L)
        assertTrue(rules.shouldResetAfterClockRollback(old,200_000L))
    }
    @Test fun previousFutureMotionTriggersReset() {
        val old=GpsAdaptiveSampling.State(startedMs=100_000L,lastMotionMs=500_000L)
        assertTrue(rules.shouldResetAfterClockRollback(old,200_000L))
    }
    @Test fun noRollbackKeepsEvidence() {
        val old=GpsAdaptiveSampling.State(startedMs=100_000L,lastMotionMs=150_000L)
        assertEquals(old,rules.observe(old,200_000L,0.0))
    }
    @Test fun timeAtSameEpochDoesNotReset() {
        val old=GpsAdaptiveSampling.State(startedMs=100_000L,lastMotionMs=150_000L)
        assertFalse(rules.shouldResetAfterClockRollback(old,150_000L))
    }
    @Test fun invalidTimeDoesNotReset() {
        val old=GpsAdaptiveSampling.State(startedMs=500_000L)
        assertFalse(rules.shouldResetAfterClockRollback(old,0L))
    }
    @Test fun disabledTrackingStillUsesActiveIntervalAfterRecovery() {
        val next=rules.observe(GpsAdaptiveSampling.State(startedMs=500_000L),
            200_000L,0.0)
        assertEquals(GpsAdaptiveSampling.ACTIVE_INTERVAL_MS,
            rules.interval(next,900_000L,false))
    }
    @Test fun invalidAccelerationDoesNotMutateState() {
        val old=GpsAdaptiveSampling.State(startedMs=500_000L)
        assertEquals(old,rules.observe(old,200_000L,Double.NaN))
    }
    @Test fun invalidTravelTimeDoesNotMutateState() {
        val old=GpsAdaptiveSampling.State(startedMs=500_000L)
        assertEquals(old,rules.gpsTravel(old,-1L))
    }
}
