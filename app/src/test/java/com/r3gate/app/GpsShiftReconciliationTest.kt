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
}
