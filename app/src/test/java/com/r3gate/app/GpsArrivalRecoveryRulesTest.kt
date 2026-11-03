package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsArrivalRecoveryRulesTest {
    private val rules=GpsArrivalRecoveryRules

    @Test fun confirmedObjectSurvivesCoverageGap() {
        val previous=GpsArrivalRules.State("site-12","site-7",50_000L,80_000L)
        assertEquals("site-12",rules.afterCoverageGap(previous).confirmedId)
    }

    @Test fun pendingArrivalCandidateIsDiscarded() {
        val previous=GpsArrivalRules.State(null,"site-12",50_000L,80_000L)
        val recovered=rules.afterCoverageGap(previous)
        assertNull(recovered.confirmedId)
        assertNull(recovered.candidateId)
        assertEquals(0L,recovered.candidateSinceMs)
    }

    @Test fun pendingDepartureCandidateIsDiscarded() {
        val previous=GpsArrivalRules.State("site-12",null,50_000L,80_000L)
        val recovered=rules.afterCoverageGap(previous)
        assertEquals("site-12",recovered.confirmedId)
        assertEquals(0L,recovered.candidateSinceMs)
    }

    @Test fun oldFixTimeIsDiscarded() {
        assertEquals(0L,rules.afterCoverageGap(
            GpsArrivalRules.State("A","B",1_000L,2_000L)).lastFixMs)
    }

    @Test fun firstOutsideFixAfterGapCannotInventDeparture() {
        val recovered=rules.afterCoverageGap(GpsArrivalRules.State("A",null,2_000L,3_000L))
        val first=GpsArrivalRules.advance(recovered,null,1_000_000L)
        assertNull(first.event)
        assertEquals("A",first.state.confirmedId)
    }

    @Test fun secondOutsideFixAfterDwellReportsDeparture() {
        val recovered=rules.afterCoverageGap(GpsArrivalRules.State("A",null,2_000L,3_000L))
        val first=GpsArrivalRules.advance(recovered,null,1_000_000L)
        val next=GpsArrivalRules.advance(first.state,null,1_061_000L)
        assertEquals("DEPARTURE",next.event)
        assertEquals("A",next.placeId)
        assertNull(next.state.confirmedId)
    }

    @Test fun duplicateOutsideFixCannotConfirmDeparture() {
        val recovered=rules.afterCoverageGap(GpsArrivalRules.State("A",null,2_000L,3_000L))
        val first=GpsArrivalRules.advance(recovered,null,1_000_000L)
        assertNull(GpsArrivalRules.advance(first.state,null,1_000_000L).event)
    }

    @Test fun returningInsideSameObjectDoesNotInventArrival() {
        val recovered=rules.afterCoverageGap(GpsArrivalRules.State("A",null,2_000L,3_000L))
        val first=GpsArrivalRules.advance(recovered,"A",1_000_000L)
        assertNull(first.event)
        assertEquals("A",first.state.confirmedId)
    }

    @Test fun uncertainBorderDoesNotGenerateDepartureAfterGap() {
        val recovered=rules.afterCoverageGap(GpsArrivalRules.State("A","B",2_000L,3_000L))
        val uncertain=GpsArrivalRules.uncertain(recovered,1_000_000L)
        assertEquals("A",uncertain.state.confirmedId)
        assertNull(uncertain.event)
    }

    @Test fun uncertaintyCancelsOutsideFixCandidate() {
        val recovered=rules.afterCoverageGap(GpsArrivalRules.State("A"))
        val first=GpsArrivalRules.advance(recovered,null,1_000_000L)
        val uncertain=GpsArrivalRules.uncertain(first.state,1_030_000L)
        val next=GpsArrivalRules.advance(uncertain.state,null,1_062_000L)
        assertNull(next.event)
    }

    @Test fun differentObjectRequiresFreshConfirmation() {
        val recovered=rules.afterCoverageGap(GpsArrivalRules.State("A","B",2_000L,3_000L))
        val first=GpsArrivalRules.advance(recovered,"B",1_000_000L)
        assertNull(first.event)
        val second=GpsArrivalRules.advance(first.state,"B",1_061_000L)
        assertEquals("ARRIVAL",second.event)
        assertEquals("B",second.placeId)
    }

    @Test fun noConfirmedZoneStaysUnknownAfterGap() {
        assertEquals(GpsArrivalRules.State(),rules.afterCoverageGap(
            GpsArrivalRules.State(null,"B",20_000L,30_000L)))
    }

    @Test fun twoOutsideFixesTooCloseDoNotReportDeparture() {
        val recovered=rules.afterCoverageGap(GpsArrivalRules.State("A"))
        val first=GpsArrivalRules.advance(recovered,null,1_000_000L)
        assertNull(GpsArrivalRules.advance(first.state,null,1_040_000L).event)
    }

    @Test fun outsideFixSeparatedByAnotherLargeGapCannotConfirmDeparture() {
        val recovered=rules.afterCoverageGap(GpsArrivalRules.State("A"))
        val first=GpsArrivalRules.advance(recovered,null,1_000_000L)
        val next=GpsArrivalRules.advance(first.state,null,1_400_001L)
        assertNull(next.event)
        assertEquals("A",next.state.confirmedId)
    }

    @Test fun missingConfirmedSiteDoesNotFabricateDeparture() {
        val state=GpsArrivalRules.State("A",null,1_000L,30_000L)
        val reset=rules.afterPlacesChanged(state,emptyList())
        assertEquals(GpsArrivalRules.State(lastFixMs=30_000L),reset)
        assertNull(GpsArrivalRules.advance(reset,null,60_000L).event)
    }

    @Test fun aNewSiteCanBeConfirmedAfterPreviousSiteWasRemoved() {
        val site=GpsWorkRules.Place("B",GpsWorkRules.Kind.OBJECT,54.0,18.0)
        val reset=rules.afterPlacesChanged(GpsArrivalRules.State("A","B",1_000L,30_000L),listOf(site))
        val first=GpsArrivalRules.advance(reset,"B",60_000L)
        assertNull(first.event)
        val second=GpsArrivalRules.advance(first.state,"B",120_000L)
        assertEquals("ARRIVAL",second.event)
        assertEquals("B",second.placeId)
    }

    @Test fun missingCandidateCannotContributeToDepartureDwell() {
        val site=GpsWorkRules.Place("A",GpsWorkRules.Kind.OBJECT,54.0,18.0)
        val reset=rules.afterPlacesChanged(GpsArrivalRules.State("A","B",1_000L,30_000L),listOf(site))
        assertEquals("A",reset.confirmedId)
        assertNull(reset.candidateId)
        assertEquals(0L,reset.candidateSinceMs)
        assertNull(GpsArrivalRules.advance(reset,null,61_000L).event)
    }

    @Test fun duplicateConfirmedSiteIsStillAmbiguous() {
        val site=GpsWorkRules.Place("A",GpsWorkRules.Kind.OBJECT,54.0,18.0)
        val places=listOf(site,site.copy(latitude=55.0))
        val saved=GpsArrivalRules.State(confirmedId="A",lastFixMs=30_000L)
        val state=rules.afterPlacesChanged(saved,places)
        val point=GpsWorkRules.Point(54.0,18.0,8.0,60_000L)
        assertEquals(saved,state)
        assertEquals(GpsGeofenceEvidence.Status.UNCERTAIN,
            GpsGeofenceEvidence.observe(point,places,state.confirmedId).status)
    }
}
