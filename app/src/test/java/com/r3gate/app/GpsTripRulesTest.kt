package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsTripRulesTest {
    @Test fun sustainedMovementStartsTrip() {
        val first=GpsTripRules.advance(GpsTripRules.State(),true,1000)
        assertNull(first.event)
        val next=GpsTripRules.advance(first.state,true,61000)
        assertEquals("TRIP_START",next.event)
        assertEquals(GpsTripRules.Mode.MOVING,next.state.mode)
    }
    @Test fun shortGpsDriftDoesNotStopTrip() {
        val current=GpsTripRules.State(mode=GpsTripRules.Mode.MOVING,lastFixMs=1000)
        val a=GpsTripRules.advance(current,false,2000)
        assertNull(GpsTripRules.advance(a.state,true,4000).event)
    }
    @Test fun sustainedStopEmitsExactlyOneStopEvent() {
        val current=GpsTripRules.State(mode=GpsTripRules.Mode.MOVING,lastFixMs=1000)
        val a=GpsTripRules.advance(current,false,2000)
        val b=GpsTripRules.advance(a.state,false,122000)
        assertEquals("TRIP_STOP",b.event)
        assertNull(GpsTripRules.advance(b.state,false,123000).event)
    }
    @Test fun longStopReportedOnceAndOldFixIgnored() {
        val state=GpsTripRules.State(mode=GpsTripRules.Mode.STOPPED,lastFixMs=1000,stoppedSinceMs=1000)
        val a=GpsTripRules.advance(state,false,601000)
        assertEquals("LONG_STOP",a.event)
        assertNull(GpsTripRules.advance(a.state,false,602000).event)
        assertEquals(a.state,GpsTripRules.advance(a.state,true,500).state)
    }
}
