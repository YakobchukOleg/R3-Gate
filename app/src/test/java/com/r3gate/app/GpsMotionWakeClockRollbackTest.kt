package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsMotionWakeClockRollbackTest {
    private val r=GpsMotionWakeRules
    private fun old()=GpsMotionWakeRules.State(
        sinceMs=500_000L,lastSampleMs=501_000L,lastWakeMs=490_000L)

    @Test fun backwardTimeResetsPriorMotionCandidate() {
        val next=r.observe(old(),200_000L,0.0)
        assertEquals(0L,next.state.sinceMs)
        assertEquals(200_000L,next.state.lastSampleMs)
    }
    @Test fun rollbackDoesNotImmediatelySignalMovement() {
        assertFalse(r.observe(old(),200_000L,3.0).wake)
    }
    @Test fun rollbackCanEstablishFreshMovementCandidate() {
        val next=r.observe(old(),200_000L,3.0)
        assertEquals(200_000L,next.state.sinceMs)
        assertEquals(0L,next.state.lastWakeMs)
    }
    @Test fun sustainedNewMovementAfterRollbackCanWake() {
        val start=r.observe(old(),200_000L,3.0)
        val mid=r.observe(start.state,201_000L,3.0)
        val end=r.observe(mid.state,202_000L,3.0)
        assertTrue(end.wake)
    }
    @Test fun gapAfterRollbackRequiresAnotherDwell() {
        val start=r.observe(old(),200_000L,3.0)
        val end=r.observe(start.state,204_000L,3.0)
        assertFalse(end.wake)
        assertEquals(204_000L,end.state.sinceMs)
    }
    @Test fun quietAfterRollbackCancelsCandidate() {
        val first=r.observe(old(),200_000L,3.0)
        val quiet=r.observe(first.state,200_500L,0.0)
        assertEquals(0L,quiet.state.sinceMs)
    }
    @Test fun futureWakeTimestampCannotSuppressIndefinitely() {
        val stale=GpsMotionWakeRules.State(lastWakeMs=800_000L,lastSampleMs=100_000L)
        val first=r.observe(stale,200_000L,3.0)
        assertEquals(0L,first.state.lastWakeMs)
    }
    @Test fun sameTimestampDoesNotDoubleCount() {
        val state=GpsMotionWakeRules.State(sinceMs=200_000L,lastSampleMs=201_000L)
        val got=r.observe(state,201_000L,3.0)
        assertFalse(got.wake)
        assertEquals(state,got.state)
    }
    @Test fun backwardsClockWhenLastSampleMissingResets() {
        val state=GpsMotionWakeRules.State(sinceMs=400_000L)
        val got=r.observe(state,200_000L,3.0)
        assertEquals(200_000L,got.state.sinceMs)
    }
    @Test fun freshWakeStillHasCooldown() {
        val start=r.observe(GpsMotionWakeRules.State(),200_000L,3.0)
        val mid=r.observe(start.state,201_000L,3.0)
        val woke=r.observe(mid.state,202_000L,3.0)
        assertTrue(woke.wake)
        val after=r.observe(woke.state,203_000L,3.0)
        val another=r.observe(after.state,205_000L,3.0)
        assertFalse(another.wake)
    }
    @Test fun invalidNegativeAccelerationKeepsState() {
        val s=old()
        assertEquals(s,r.observe(s,200_000L,-1.0).state)
    }
    @Test fun invalidNanKeepsState() {
        val s=old()
        assertEquals(s,r.observe(s,200_000L,Double.NaN).state)
    }
    @Test fun nonpositiveTimestampKeepsState() {
        val s=old()
        assertEquals(s,r.observe(s,0L,2.0).state)
    }
    @Test fun normalSequenceUnchanged() {
        val start=r.observe(GpsMotionWakeRules.State(),1_000L,2.0)
        val after=r.observe(start.state,2_000L,2.0)
        val last=r.observe(after.state,3_000L,2.0)
        assertTrue(last.wake)
    }
}
