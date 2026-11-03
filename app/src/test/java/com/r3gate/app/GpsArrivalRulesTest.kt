package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsArrivalRulesTest {
    @Test fun onlyConfirmsArrivalAfterSecondStableFix() {
        val a=GpsArrivalRules.advance(GpsArrivalRules.State(), "A",1000)
        assertNull(a.event)
        val b=GpsArrivalRules.advance(a.state,"A",61000)
        assertEquals("ARRIVAL",b.event)
        assertEquals("A",b.state.confirmedId)
    }
    @Test fun driftAndOutOfOrderFixesDoNotCreateFalseTransitions() {
        val a=GpsArrivalRules.advance(GpsArrivalRules.State(),"A",1000)
        val b=GpsArrivalRules.advance(a.state,null,2000)
        assertNull(GpsArrivalRules.advance(b.state,"A",3000).event)
        assertEquals(b.state,GpsArrivalRules.advance(b.state,"A",1500).state)
    }
    @Test fun departureRequiresTwoOutsideFixes() {
        val arrived=GpsArrivalRules.State(confirmedId="A",lastFixMs=1000)
        val a=GpsArrivalRules.advance(arrived,null,2000)
        val b=GpsArrivalRules.advance(a.state,null,62000)
        assertEquals("DEPARTURE",b.event)
        assertEquals("A",b.placeId)
        assertNull(b.state.confirmedId)
    }
    @Test fun switchingObjectsRequiresStableNewPlace() {
        val state=GpsArrivalRules.State(confirmedId="A",lastFixMs=1000)
        val first=GpsArrivalRules.advance(state,"B",2000)
        val second=GpsArrivalRules.advance(first.state,"B",62000)
        assertEquals("ARRIVAL",second.event)
        assertEquals("B",second.state.confirmedId)
    }
    @Test fun longGapCannotCompletePendingArrival() {
        val start=GpsArrivalRules.advance(GpsArrivalRules.State(),"A",1000)
        val afterGap=GpsArrivalRules.advance(start.state,"A",400_000)
        assertNull(afterGap.event)
        assertNull(afterGap.state.confirmedId)
        assertEquals(400_000L,afterGap.state.candidateSinceMs)
        assertEquals("ARRIVAL",GpsArrivalRules.advance(afterGap.state,"A",461_000).event)
    }
    @Test fun longGapCannotCompleteDepartureAndKeepsConfirmedPlace() {
        val start=GpsArrivalRules.advance(
            GpsArrivalRules.State(confirmedId="A",lastFixMs=1000),null,2000)
        val afterGap=GpsArrivalRules.advance(start.state,null,400_000)
        assertNull(afterGap.event)
        assertEquals("A",afterGap.state.confirmedId)
        assertEquals("DEPARTURE",GpsArrivalRules.advance(afterGap.state,null,461_000).event)
    }
    @Test fun longGapWithoutNewObservationDoesNotInventArrival() {
        val state=GpsArrivalRules.State(confirmedId="A",lastFixMs=1000)
        val afterGap=GpsArrivalRules.advance(state,"A",400_000)
        assertNull(afterGap.event)
        assertEquals("A",afterGap.state.confirmedId)
        assertNull(GpsArrivalRules.advance(afterGap.state,"A",461_000).event)
    }
    @Test fun uncertainFixCancelsPendingArrival() {
        val pending=GpsArrivalRules.advance(GpsArrivalRules.State(),"A",1000)
        val uncertain=GpsArrivalRules.uncertain(pending.state,30_000)
        assertNull(uncertain.event)
        assertNull(uncertain.state.candidateId)
        assertEquals(0L,uncertain.state.candidateSinceMs)
        assertNull(GpsArrivalRules.advance(uncertain.state,"A",61_000).event)
    }

    @Test fun arrivalAfterUncertaintyRequiresFreshDwell() {
        val pending=GpsArrivalRules.advance(GpsArrivalRules.State(),"A",1000)
        val uncertain=GpsArrivalRules.uncertain(pending.state,30_000)
        val first=GpsArrivalRules.advance(uncertain.state,"A",60_000)
        assertNull(first.event)
        assertEquals("ARRIVAL",GpsArrivalRules.advance(first.state,"A",121_000).event)
    }

    @Test fun uncertainFixCancelsPendingDepartureWithoutLosingSite() {
        val active=GpsArrivalRules.State(confirmedId="A",lastFixMs=1000)
        val pending=GpsArrivalRules.advance(active,null,2000)
        val uncertain=GpsArrivalRules.uncertain(pending.state,45_000)
        assertEquals("A",uncertain.state.confirmedId)
        assertEquals(0L,uncertain.state.candidateSinceMs)
        assertNull(GpsArrivalRules.advance(uncertain.state,null,65_000).event)
    }

    @Test fun departureAfterUncertaintyRequiresFreshDwell() {
        val active=GpsArrivalRules.State(confirmedId="A",lastFixMs=1000)
        val pending=GpsArrivalRules.advance(active,null,2000)
        val uncertain=GpsArrivalRules.uncertain(pending.state,30_000)
        val first=GpsArrivalRules.advance(uncertain.state,null,60_000)
        assertNull(first.event)
        assertEquals("DEPARTURE",GpsArrivalRules.advance(first.state,null,121_000).event)
    }

    @Test fun oldUncertainFixCannotRewindValidState() {
        val state=GpsArrivalRules.State(confirmedId="A",candidateId="B",
            candidateSinceMs=25_000,lastFixMs=30_000)
        assertEquals(state,GpsArrivalRules.uncertain(state,20_000).state)
        assertEquals(state,GpsArrivalRules.uncertain(state,30_000).state)
    }

    @Test fun uncertainFixPreservesAlreadyConfirmedLocation() {
        val state=GpsArrivalRules.State(confirmedId="site",lastFixMs=1000)
        val result=GpsArrivalRules.uncertain(state,20_000)
        assertEquals("site",result.state.confirmedId)
        assertNull(result.event)
        assertEquals(20_000L,result.state.lastFixMs)
        assertNull(GpsArrivalRules.advance(result.state,"site",21_000).event)
    }

}
