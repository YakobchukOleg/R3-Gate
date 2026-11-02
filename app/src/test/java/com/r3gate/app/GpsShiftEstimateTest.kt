package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsShiftEstimateTest {
    private fun e(t:String,ms:Long)=GpsShiftEstimate.Event(t,ms)
    private val shift=GpsShiftEstimate.Window(100,1100)
    @Test fun trimsTripsToShiftAndCountsOnlyCompletedPairs() {
        val events=listOf(e("TRIP_START",50),e("TRIP_STOP",300),
            e("TRIP_START",800),e("TRIP_STOP",1300),e("TRIP_START",1500))
        assertEquals(GpsShiftEstimate.Summary(500,500,2),
            GpsShiftEstimate.calculate(events,shift,true))
    }
    @Test fun unpaidTripsStillCountAsActualAndDoNotTouchWorkTime() {
        assertEquals(GpsShiftEstimate.Summary(400,0,1),
            GpsShiftEstimate.calculate(listOf(e("TRIP_START",200),e("TRIP_STOP",600)),shift,false))
    }
    @Test fun stopAndLongStopDoNotFabricateTime() {
        assertEquals(GpsShiftEstimate.Summary(0,0,0),
            GpsShiftEstimate.calculate(listOf(e("TRIP_STOP",300),e("LONG_STOP",600)),shift,true))
    }
    @Test fun ignoresInvalidShiftAndChronologicallyOrdersEvents() {
        val events=listOf(e("TRIP_STOP",600),e("TRIP_START",200))
        assertEquals(400L,GpsShiftEstimate.calculate(events,shift,true).actualTravelMs)
        assertEquals(0L,GpsShiftEstimate.calculate(events,GpsShiftEstimate.Window(100,100),true).actualTravelMs)
    }
    @Test fun restartCannotCompleteTripStartedBeforeGpsStopped() {
        val events=listOf(e("TRIP_START",100),e("GPS_GAP",200),e("TRIP_STOP",800),
            e("TRIP_START",850),e("TRIP_STOP",950))
        assertEquals(GpsShiftEstimate.Summary(100,100,1),
            GpsShiftEstimate.calculate(events,shift,true))
    }
    @Test fun restartRecoveryUsesLatestStartInsteadOfOrphanStart() {
        val events=listOf(e("TRIP_START",200),e("TRIP_START",600),e("TRIP_STOP",900))
        assertEquals(GpsShiftEstimate.Summary(300,300,1),
            GpsShiftEstimate.calculate(events,shift,true))
    }
    @Test fun gapAndLaterStartNeverChargeMissingGpsCoverage() {
        val events=listOf(e("TRIP_START",100),e("GPS_GAP",300),
            e("TRIP_START",800),e("TRIP_STOP",1000))
        assertEquals(GpsShiftEstimate.Summary(200,0,1),
            GpsShiftEstimate.calculate(events,shift,false))
    }
}
