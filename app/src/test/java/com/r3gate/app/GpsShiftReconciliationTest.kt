package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsShiftReconciliationTest {
    private fun e(type:String,at:Long)=GpsShiftEstimate.Event(type,at)
    @Test fun separatesTravelInsideAndOutsideWorkShift() {
        val result=GpsShiftReconciliation.review(listOf(e("TRIP_START",50),e("TRIP_STOP",250)),
            GpsShiftReconciliation.Window(100,200),listOf(100,150,200),60)
        assertEquals(100L,result.travelInsideMs)
        assertEquals(100L,result.travelOutsideMs)
        assertTrue(result.requiresReview)
    }
    @Test fun missingLocationFixesAreFlaggedNotCountedAsWorkingTime() {
        val result=GpsShiftReconciliation.review(emptyList(),GpsShiftReconciliation.Window(0,1_800_000),
            listOf(100_000,1_700_000),900_000)
        assertEquals(1,result.missingLocationIntervals)
        assertEquals(0L,result.travelInsideMs)
        assertTrue(result.requiresReview)
    }
    @Test fun incompleteAndOverlappingTripsRequireManualReview() {
        val result=GpsShiftReconciliation.review(listOf(e("TRIP_START",200),
            e("TRIP_START",300),e("TRIP_STOP",400),e("TRIP_START",800)),
            GpsShiftReconciliation.Window(100,900),listOf(100,500,900),500)
        assertEquals(1,result.overlappingTrips)
        assertEquals(1,result.incompleteTripStarts)
        assertTrue(result.requiresReview)
    }
    @Test fun cleanTripsAndDenseFixesNeedNoReview() {
        val result=GpsShiftReconciliation.review(listOf(e("TRIP_START",200),e("TRIP_STOP",400)),
            GpsShiftReconciliation.Window(100,500),listOf(100,250,400,500),200)
        assertEquals(1,result.completedTrips)
        assertEquals(200L,result.travelInsideMs)
        assertFalse(result.requiresReview)
    }
    @Test fun invalidWindowYieldsNoFabricatedDurations() {
        assertEquals(0L,GpsShiftReconciliation.review(
            listOf(e("TRIP_START",1),e("TRIP_STOP",500)),GpsShiftReconciliation.Window(800,700)).travelInsideMs)
    }
    @Test fun interruptedGpsTripRequiresReviewWithoutInventingDuration() {
        val result=GpsShiftReconciliation.review(listOf(e("TRIP_START",200),
            e("GPS_GAP",250),e("TRIP_STOP",400)),
            GpsShiftReconciliation.Window(100,500),listOf(100,200,300,400,500),250)
        assertEquals(0,result.completedTrips)
        assertEquals(0L,result.travelInsideMs)
        assertEquals(1,result.incompleteTripStarts)
        assertTrue(result.requiresReview)
    }
    @Test fun consecutiveTripsSplitAcrossWindowHaveCorrectInsideAndOutsideTotals() {
        val events=listOf(e("TRIP_START",50),e("TRIP_STOP",150),
            e("TRIP_START",150),e("TRIP_STOP",250),
            e("TRIP_START",250),e("TRIP_STOP",350))
        val review=GpsShiftReconciliation.review(events,
            GpsShiftReconciliation.Window(100,300),
            listOf(100,150,200,250,300),100)
        assertEquals(3,review.completedTrips)
        assertEquals(200L,review.travelInsideMs)
        assertEquals(100L,review.travelOutsideMs)
    }
    @Test fun gpsGapExcludesInterruptedTravelFromReconciliation() {
        val events=listOf(e("TRIP_START",100),e("GPS_GAP",150),
            e("TRIP_START",400),e("TRIP_STOP",550))
        val review=GpsShiftReconciliation.review(events,
            GpsShiftReconciliation.Window(50,600),
            listOf(50,100,150,400,550,600),300)
        assertEquals(150L,review.travelInsideMs)
        assertEquals(1,review.incompleteTripStarts)
        assertTrue(review.requiresReview)
    }
}
