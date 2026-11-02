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
    @Test fun shortGpsDropoutDoesNotCreateTripEndOrNewTrip() {
        val moving=GpsTripRules.State(mode=GpsTripRules.Mode.MOVING,lastFixMs=1_000)
        val briefStop=GpsTripRules.advance(moving,false,91_000)
        assertNull(briefStop.event)
        assertEquals(GpsTripRules.Mode.MOVING,briefStop.state.mode)
        val resumed=GpsTripRules.advance(briefStop.state,true,181_000)
        assertNull(resumed.event)
        assertEquals(GpsTripRules.Mode.MOVING,resumed.state.mode)
    }
    @Test fun repeatedRegularStationaryFixesLeadToExactlyOneLongStop() {
        var state=GpsTripRules.State(mode=GpsTripRules.Mode.STOPPED,
            lastFixMs=1_000,stoppedSinceMs=1_000)
        var reports=0
        for(time in 91_000L..721_000L step 90_000L) {
            val next=GpsTripRules.advance(state,false,time)
            if(next.event=="LONG_STOP") reports++
            state=next.state
        }
        assertEquals(1,reports)
        assertTrue(state.longStopReported)
    }
    @Test fun gapDuringStopCandidateForcesFreshStopConfirmation() {
        val first=GpsTripRules.advance(
            GpsTripRules.State(mode=GpsTripRules.Mode.MOVING,lastFixMs=1_000),
            false,91_000)
        val afterGap=GpsTripRules.advance(first.state,false,400_000)
        assertNull(afterGap.event)
        assertEquals(GpsTripRules.Mode.UNKNOWN,afterGap.state.mode)
        val again=GpsTripRules.advance(afterGap.state,false,490_000)
        assertNull(again.event)
        val confirmed=GpsTripRules.advance(again.state,false,580_000)
        assertNull(confirmed.event)
        assertEquals(GpsTripRules.Mode.STOPPED,confirmed.state.mode)
    }
    @Test fun startingStationaryNeverCreatesPhantomTripStop() {
        val first=GpsTripRules.advance(GpsTripRules.State(),false,1_000)
        val second=GpsTripRules.advance(first.state,false,121_000)
        assertNull(first.event)
        assertNull(second.event)
        assertEquals(GpsTripRules.Mode.STOPPED,second.state.mode)
    }

    @Test fun longStationaryStartupProducesNoTripStop() {
        var state=GpsTripRules.State()
        var stops=0
        for(t in listOf(1_000L,61_000L,121_000L,181_000L,241_000L)) {
            val result=GpsTripRules.advance(state,false,t)
            if(result.event=="TRIP_STOP") stops++
            state=result.state
        }
        assertEquals(0,stops)
        assertEquals(GpsTripRules.Mode.STOPPED,state.mode)
    }

    @Test fun afterGpsOutageStationaryReacquisitionDoesNotEndUnknownTrip() {
        val moving=GpsTripRules.State(mode=GpsTripRules.Mode.MOVING,lastFixMs=1_000)
        val gap=GpsTripRules.advance(moving,false,300_000)
        val recovered=GpsTripRules.advance(gap.state,false,421_000)
        assertNull(gap.event)
        assertNull(recovered.event)
        assertEquals(GpsTripRules.Mode.STOPPED,recovered.state.mode)
    }

    @Test fun trueMovingToStoppedTransitionStillEndsTrip() {
        val moving=GpsTripRules.State(mode=GpsTripRules.Mode.MOVING,lastFixMs=1_000)
        val first=GpsTripRules.advance(moving,false,31_000)
        val second=GpsTripRules.advance(first.state,false,151_000)
        assertEquals("TRIP_STOP",second.event)
        assertEquals(GpsTripRules.Mode.STOPPED,second.state.mode)
    }

    @Test fun stationaryToMovementStartsNewTripNormally() {
        val idle=GpsTripRules.State(mode=GpsTripRules.Mode.STOPPED,
            stoppedSinceMs=1_000,lastFixMs=1_000)
        val first=GpsTripRules.advance(idle,true,31_000)
        val second=GpsTripRules.advance(first.state,true,91_000)
        assertNull(first.event)
        assertEquals("TRIP_START",second.event)
    }

    @Test fun duplicateFixAfterStartupDoesNotCreateEvent() {
        val first=GpsTripRules.advance(GpsTripRules.State(),false,1_000)
        val repeated=GpsTripRules.advance(first.state,false,1_000)
        assertNull(repeated.event)
        assertEquals(first.state,repeated.state)
    }

    @Test fun confirmedStoppedModeDoesNotRepeatStopEvent() {
        val idle=GpsTripRules.State(mode=GpsTripRules.Mode.STOPPED,
            stoppedSinceMs=1_000,lastFixMs=1_000)
        val repeated=GpsTripRules.advance(idle,false,61_000)
        assertNull(repeated.event)
        assertEquals(GpsTripRules.Mode.STOPPED,repeated.state.mode)
    }

}
