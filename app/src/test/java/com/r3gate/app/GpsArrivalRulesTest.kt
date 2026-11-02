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
}
