package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsShopTripMatchTest {
    private fun e(kind:String,time:Long)=GpsShiftEstimate.Event(kind,time)
    @Test fun matchesOnlyCompletedTripEndingAtShopStop() {
        val trip=GpsShopTripMatch.matchingTrip(listOf(
            e("TRIP_START",100),e("TRIP_STOP",200),
            e("TRIP_START",300),e("TRIP_STOP",500)),500,true)
        assertEquals(300L,trip?.startMs)
        assertEquals(500L,trip?.endMs)
    }
    @Test fun neverMatchesUnmarkedShopOrUnrelatedStop() {
        val events=listOf(e("TRIP_START",100),e("TRIP_STOP",200))
        assertNull(GpsShopTripMatch.matchingTrip(events,200,false))
        assertNull(GpsShopTripMatch.matchingTrip(events,300,true))
    }
    @Test fun interruptionDoesNotOfferOrphanedTrip() {
        val events=listOf(e("TRIP_START",100),e("GPS_GAP",150),e("TRIP_STOP",200))
        assertNull(GpsShopTripMatch.matchingTrip(events,200,true))
    }
    @Test fun duplicateMatchedTripsAreNotAccepted() {
        val events=listOf(e("TRIP_START",100),e("TRIP_STOP",200),
            e("TRIP_START",150),e("TRIP_STOP",200))
        assertNull(GpsShopTripMatch.matchingTrip(events,200,true))
    }
}
