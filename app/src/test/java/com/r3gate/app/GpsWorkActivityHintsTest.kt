package com.r3gate.app
import org.junit.Assert.*
import org.junit.Test

class GpsWorkActivityHintsTest {
    private fun e(type:String,t:Long,id:String?=null)=GpsWorkActivityHints.Event(type,t,id)
    @Test fun confirmedArrivalAndNearbyStopSuggestWorksite() {
        assertEquals("ob1",GpsWorkActivityHints.suggest(listOf(
            e("ARRIVAL",1000,"ob1"),e("TRIP_STOP",61_000)),100_000)?.objectId)
    }
    @Test fun drivingPastWorksiteNeverSuggestsWork() {
        assertNull(GpsWorkActivityHints.suggest(listOf(e("ARRIVAL",1000,"ob1"),
            e("TRIP_START",2000),e("TRIP_STOP",3000)),4000))
    }
    @Test fun signalGapInvalidatesOldArrival() {
        assertNull(GpsWorkActivityHints.suggest(listOf(e("ARRIVAL",1000,"ob1"),
            e("GPS_GAP",2000),e("TRIP_STOP",3000)),4000))
    }
    @Test fun stopTooLateAfterArrivalIsNotWorkConfirmation() {
        assertNull(GpsWorkActivityHints.suggest(listOf(e("ARRIVAL",1000,"ob1"),
            e("TRIP_STOP",400_000)),401_000))
    }
    @Test fun oldAndDepartedSuggestionsDisappear() {
        val base=listOf(e("ARRIVAL",1000,"ob1"),e("TRIP_STOP",2000))
        assertNull(GpsWorkActivityHints.suggest(base,1_300_001))
        assertNull(GpsWorkActivityHints.suggest(base+e("DEPARTURE",3000),4000))
    }
}
