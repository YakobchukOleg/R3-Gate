package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsShopVisitHistoryTest {
    private fun e(type:String,time:Long,id:String?=null)=GpsShopVisitHistory.Event(type,time,id)
    @Test fun repeatedConfirmedVisitsToSameShopAreCounted() {
        val events=listOf(e("ARRIVAL",1000,"shop"),e("TRIP_STOP",61000),
            e("DEPARTURE",100000),e("ARRIVAL",200000,"shop"),e("TRIP_STOP",260000))
        assertEquals(2,GpsShopVisitHistory.confirmedVisits(events,"shop",300000))
    }
    @Test fun passingAndInterruptedTrackingNeverCountAsVisits() {
        val events=listOf(e("ARRIVAL",1000,"shop"),e("TRIP_START",2000),e("TRIP_STOP",3000),
            e("ARRIVAL",4000,"shop"),e("GPS_GAP",5000),e("TRIP_STOP",6000))
        assertEquals(0,GpsShopVisitHistory.confirmedVisits(events,"shop",7000))
    }
    @Test fun anotherLocationCannotCountForThisShop() {
        val events=listOf(e("ARRIVAL",1000,"other"),e("TRIP_STOP",61000))
        assertEquals(0,GpsShopVisitHistory.confirmedVisits(events,"shop",62000))
    }
    @Test fun expiredAndLateStopCannotCount() {
        val now=GpsShopVisitHistory.LOOKBACK_MS+1_000_000L
        val events=listOf(e("ARRIVAL",1000,"shop"),e("TRIP_STOP",61000),
            e("ARRIVAL",now-400000,"shop"),e("TRIP_STOP",now))
        assertEquals(0,GpsShopVisitHistory.confirmedVisits(events,"shop",now))
    }
    @Test fun duplicatedStopCannotMultiplyVisitCount() {
        val events=listOf(e("ARRIVAL",1000,"shop"),e("TRIP_STOP",61000),e("TRIP_STOP",62000))
        assertEquals(1,GpsShopVisitHistory.confirmedVisits(events,"shop",63000))
    }
}
