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
    @Test fun longStopRequiresContinuousFixesAndOldFixIgnored() {
        val state=GpsTripRules.State(mode=GpsTripRules.Mode.STOPPED,lastFixMs=1000,stoppedSinceMs=1000)
        val afterGap=GpsTripRules.advance(state,false,601000)
        assertNull(afterGap.event)
        assertEquals(GpsTripRules.Mode.UNKNOWN,afterGap.state.mode)
        assertEquals(afterGap.state,GpsTripRules.advance(afterGap.state,true,500).state)
        val confirmed=GpsTripRules.advance(afterGap.state,false,722000)
        assertEquals(GpsTripRules.Mode.STOPPED,confirmed.state.mode)
        assertNull(confirmed.event.takeIf { it=="LONG_STOP" })
        val longStop=GpsTripRules.advance(confirmed.state,false,1_322_000)
        assertNull(longStop.event) // The 10-minute blind gap is not observed stationary time.
    }
    @Test fun movementAfterLongGpsSilenceNeedsFreshConfirmation() {
        val previous=GpsTripRules.State(mode=GpsTripRules.Mode.MOVING,
            lastFixMs=1_000,candidate=GpsTripRules.Mode.STOPPED,candidateSinceMs=2_000)
        val resumed=GpsTripRules.advance(previous,true,300_000)
        assertNull(resumed.event)
        assertEquals(GpsTripRules.Mode.UNKNOWN,resumed.state.mode)
        assertEquals(GpsTripRules.Mode.MOVING,resumed.state.candidate)
        assertNull(GpsTripRules.advance(resumed.state,true,350_000).event)
        assertEquals("TRIP_START",GpsTripRules.advance(resumed.state,true,361_000).event)
    }
    @Test fun staleStationaryEvidenceNeverProducesAutomaticTripStop() {
        val state=GpsTripRules.State(mode=GpsTripRules.Mode.MOVING,
            candidate=GpsTripRules.Mode.STOPPED,candidateSinceMs=2_000,lastFixMs=30_000)
        val resumed=GpsTripRules.advance(state,false,320_000)
        assertNull(resumed.event)
        assertEquals(GpsTripRules.Mode.UNKNOWN,resumed.state.mode)
        assertEquals(GpsTripRules.Mode.STOPPED,resumed.state.candidate)
    }
    @Test fun trafficLightAndTrafficJamDoNotSplitTripPrematurely() {
        var state=GpsTripRules.State(mode=GpsTripRules.Mode.MOVING,lastFixMs=1000)
        state=GpsTripRules.advance(state,false,31_000).state
        state=GpsTripRules.advance(state,false,91_000).state
        assertEquals(GpsTripRules.Mode.MOVING,state.mode)
        val resumed=GpsTripRules.advance(state,true,121_000)
        assertNull(resumed.event)
        assertEquals(GpsTripRules.Mode.MOVING,resumed.state.mode)
    }
    @Test fun confirmedContinuousStopEndsTripAfterDwell() {
        var state=GpsTripRules.State(mode=GpsTripRules.Mode.MOVING,lastFixMs=1000)
        state=GpsTripRules.advance(state,false,31_000).state
        state=GpsTripRules.advance(state,false,91_000).state
        val stop=GpsTripRules.advance(state,false,151_000)
        assertEquals("TRIP_STOP",stop.event)
        assertEquals(GpsTripRules.Mode.STOPPED,stop.state.mode)
    }
}
