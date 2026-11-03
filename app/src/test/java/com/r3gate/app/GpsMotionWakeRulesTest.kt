package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsMotionWakeRulesTest {
    private val r=GpsMotionWakeRules
    private val empty=GpsMotionWakeRules.State()
    @Test fun isolatedJoltDoesNotTrigger() { assertFalse(r.observe(empty,1000,3.0).wake) }
    @Test fun sustainedAccelerationTriggersWake() {
        val a=r.observe(empty,1000,3.0).state
        assertTrue(r.observe(a,3000,3.0).wake)
    }
    @Test fun insufficientDwellDoesNotWake() {
        val a=r.observe(empty,1000,3.0).state
        assertFalse(r.observe(a,2999,3.0).wake)
    }
    @Test fun quietObservationResetsCandidate() {
        val a=r.observe(empty,1000,3.0).state
        val b=r.observe(a,2000,0.1).state
        assertFalse(r.observe(b,3000,3.0).wake)
    }
    @Test fun missingSensorSamplesResetDwell() {
        val a=r.observe(empty,1000,3.0).state
        assertFalse(r.observe(a,5000,3.0).wake)
    }
    @Test fun immediateSecondWakeIsThrottled() {
        val a=r.observe(empty,1000,3.0).state
        val b=r.observe(a,3000,3.0).state
        val c=r.observe(b,4000,3.0).state
        assertFalse(r.observe(c,6000,3.0).wake)
    }
    @Test fun secondWakeAfterCooldownIsAllowed() {
        val a=r.observe(empty,1000,3.0).state
        val b=r.observe(a,3000,3.0).state
        val c=r.observe(b,62_000,3.0).state
        assertTrue(r.observe(c,64_000,3.0).wake)
    }
    @Test fun duplicatedTimestampIgnored() {
        val a=r.observe(empty,1000,3.0).state
        assertEquals(a,r.observe(a,1000,3.0).state)
    }
    @Test fun backwardsTimestampIgnored() {
        val a=r.observe(empty,1000,3.0).state
        assertEquals(a,r.observe(a,999,3.0).state)
    }
    @Test fun invalidNaNValueIgnored() { assertEquals(empty,r.observe(empty,1000,Double.NaN).state) }
    @Test fun negativeValueIgnored() { assertEquals(empty,r.observe(empty,1000,-1.0).state) }
    @Test fun infiniteValueIgnored() { assertEquals(empty,r.observe(empty,1000,Double.POSITIVE_INFINITY).state) }
    @Test fun atThresholdCanStartCandidate() { assertEquals(1000,r.observe(empty,1000,1.5).state.sinceMs) }
}
