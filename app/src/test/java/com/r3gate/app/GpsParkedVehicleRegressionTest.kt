package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

/** GPS-only end-to-end decisions: sitting in a stationary vehicle is not a trip. */
class GpsParkedVehicleRegressionTest {
    private val site=GpsWorkRules.Place("site",GpsWorkRules.Kind.OBJECT,54.092,18.776,100.0)
    private val initial=GpsWorkRules.Point(54.092,18.776,8.0,1_000L)

    @Test fun stationaryAtSiteDoesNotInferDepartureOrTrip() {
        var arrival=GpsArrivalRules.State(confirmedId="site",lastFixMs=1_000L)
        var trip=GpsTripRules.State()
        var previous=initial
        for(time in listOf(31_000L,61_000L,91_000L,121_000L,151_000L,181_000L)) {
            val current=initial.copy(latitude=54.09201,timestampMs=time)
            val observed=GpsWorkRules.nearest(current,listOf(site))?.place?.id
            val zone=GpsArrivalRules.advance(arrival,observed,time)
            arrival=zone.state
            assertNull(zone.event)
            assertEquals("site",arrival.confirmedId)
            val moving=GpsWorkRules.traveling(previous,current)
            assertFalse(moving)
            val result=GpsTripRules.advance(trip,moving,time)
            trip=result.state
            assertNotEquals("TRIP_START",result.event)
            previous=current
        }
    }

    @Test fun confirmedDepartureRequiresMultipleOutsideFixes() {
        var arrival=GpsArrivalRules.State(confirmedId="site",lastFixMs=1_000L)
        val first=GpsArrivalRules.advance(arrival,null,31_000L)
        assertNull(first.event)
        assertEquals("site",first.state.confirmedId)
        arrival=first.state
        val second=GpsArrivalRules.advance(arrival,null,91_000L)
        assertEquals("DEPARTURE",second.event)
        assertEquals("site",second.placeId)
    }
}
