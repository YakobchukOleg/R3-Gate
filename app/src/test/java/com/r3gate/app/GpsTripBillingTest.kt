package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsTripBillingTest {
    private fun e(type:String,time:Long)=GpsShiftEstimate.Event(type,time)
    @Test fun onlyExplicitlyCategorizedTravelCanBePaid() {
        val events=listOf(e("TRIP_START",100),e("TRIP_STOP",300),e("TRIP_START",400),e("TRIP_STOP",700))
        val trips=GpsTripBilling.pair(events,mapOf(400L to GpsTripBilling.Category.BETWEEN))
        assertEquals(GpsTripBilling.Total(500,300,2,1),
            GpsTripBilling.calculate(trips,GpsShiftEstimate.Window(0,1000),GpsTripBilling.Rules()))
    }
    @Test fun keepsUncategorizedAndPrivateTripsUnpaid() {
        val trips=listOf(GpsTripBilling.Trip(100,200),GpsTripBilling.Trip(250,450,GpsTripBilling.Category.PRIVATE))
        assertEquals(GpsTripBilling.Total(300,0,2,1),
            GpsTripBilling.calculate(trips,GpsShiftEstimate.Window(0,1000),GpsTripBilling.Rules(true,true,true,true)))
    }
    @Test fun respectsEachCommuteToggleAndShiftWindow() {
        val trips=listOf(GpsTripBilling.Trip(0,500,GpsTripBilling.Category.OUTBOUND),
            GpsTripBilling.Trip(500,900,GpsTripBilling.Category.RETURN))
        assertEquals(GpsTripBilling.Total(600,200,2,0),GpsTripBilling.calculate(
            trips,GpsShiftEstimate.Window(300,1100),GpsTripBilling.Rules(outbound=true)))
    }
    @Test fun ignoresIncompleteInvalidAndOutOfWindowTrips() {
        val trips=GpsTripBilling.pair(listOf(e("TRIP_STOP",50),e("TRIP_START",100),
            e("LONG_STOP",200),e("TRIP_STOP",300),e("TRIP_START",400)),emptyMap())
        assertEquals(1,trips.size)
        assertEquals(0L,GpsTripBilling.calculate(trips,GpsShiftEstimate.Window(500,800),GpsTripBilling.Rules()).elapsedMs)
    }
    @Test fun neverBillsAcrossRestartOrSignalOutage() {
        val events=listOf(e("TRIP_START",100),e("GPS_GAP",150),e("TRIP_STOP",500),
            e("TRIP_START",600),e("TRIP_STOP",800))
        val trips=GpsTripBilling.pair(events,mapOf(100L to GpsTripBilling.Category.BETWEEN,
            600L to GpsTripBilling.Category.BETWEEN))
        assertEquals(1,trips.size)
        assertEquals(600L,trips.single().startMs)
        assertEquals(200L,GpsTripBilling.calculate(trips,GpsShiftEstimate.Window(0,1000),
            GpsTripBilling.Rules()).payableMs)
    }
    @Test fun newStartAfterInterruptedTrackingReplacesOrphanStart() {
        val events=listOf(e("TRIP_START",100),e("TRIP_START",500),e("TRIP_STOP",800))
        val trips=GpsTripBilling.pair(events,mapOf(
            100L to GpsTripBilling.Category.BETWEEN,
            500L to GpsTripBilling.Category.BETWEEN))
        assertEquals(1,trips.size)
        assertEquals(500L,trips.single().startMs)
        assertEquals(300L,GpsTripBilling.calculate(trips,GpsShiftEstimate.Window(0,1000),
            GpsTripBilling.Rules()).payableMs)
    }
    @Test fun freshStartFollowingGapOnlyPairsWithItsOwnStop() {
        val events=listOf(e("TRIP_START",100),e("GPS_GAP",200),
            e("TRIP_START",700),e("TRIP_STOP",950))
        val trips=GpsTripBilling.pair(events,mapOf(100L to GpsTripBilling.Category.BETWEEN,
            700L to GpsTripBilling.Category.BETWEEN))
        assertEquals(listOf(GpsTripBilling.Trip(700,950,GpsTripBilling.Category.BETWEEN)),trips)
    }
    @Test fun overlappingTripsDoNotDoubleCountTravelOrPayableMinutes() {
        val trips=listOf(
            GpsTripBilling.Trip(100,500,GpsTripBilling.Category.BETWEEN),
            GpsTripBilling.Trip(300,700,GpsTripBilling.Category.BETWEEN))
        assertEquals(GpsTripBilling.Total(600,600,2,0),
            GpsTripBilling.calculate(trips,GpsShiftEstimate.Window(0,1000),GpsTripBilling.Rules()))
    }
    @Test fun unpaidOverlapDoesNotEraseLegitimatePaidCoverage() {
        val trips=listOf(
            GpsTripBilling.Trip(100,600,GpsTripBilling.Category.PRIVATE),
            GpsTripBilling.Trip(400,800,GpsTripBilling.Category.SHOPPING))
        assertEquals(GpsTripBilling.Total(700,400,2,0),
            GpsTripBilling.calculate(trips,GpsShiftEstimate.Window(0,1000),GpsTripBilling.Rules()))
    }
    @Test fun duplicatesAndWindowClippingCannotExceedShiftDuration() {
        val trips=listOf(
            GpsTripBilling.Trip(0,1200,GpsTripBilling.Category.BETWEEN),
            GpsTripBilling.Trip(0,1200,GpsTripBilling.Category.BETWEEN))
        assertEquals(GpsTripBilling.Total(800,800,2,0),
            GpsTripBilling.calculate(trips,GpsShiftEstimate.Window(200,1000),GpsTripBilling.Rules()))
    }
}
