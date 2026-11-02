package com.r3gate.app
import org.junit.Assert.*
import org.junit.Test

class GpsActivitySuggestionRulesTest {
    private fun e(type:String,t:Long,id:String?=null)=GpsActivitySuggestionRules.Event(type,t,id)
    @Test fun worksiteRequiresConfirmedArrivalAndTripStop() {
        val events=listOf(e("ARRIVAL",1000,"site"),e("TRIP_STOP",61000))
        assertEquals(GpsActivitySuggestionRules.Kind.WORKSITE,
            GpsActivitySuggestionRules.latest(events,mapOf("site" to GpsActivitySuggestionRules.Kind.WORKSITE),62000)?.kind)
    }
    @Test fun shopMustBeExplicitlyTaggedNeverGuessed() {
        val events=listOf(e("ARRIVAL",1000,"shop"),e("TRIP_STOP",61000))
        assertNull(GpsActivitySuggestionRules.latest(events,emptyMap(),62000))
        assertEquals(GpsActivitySuggestionRules.Kind.SHOPPING,
            GpsActivitySuggestionRules.latest(events,mapOf("shop" to GpsActivitySuggestionRules.Kind.SHOPPING),62000)?.kind)
    }
    @Test fun longStopWithoutPlaceIsOnlyStopHint() {
        val events=listOf(e("TRIP_STOP",1000),e("LONG_STOP",602000))
        assertEquals(GpsActivitySuggestionRules.Kind.STOP,
            GpsActivitySuggestionRules.latest(events,emptyMap(),603000)?.kind)
    }
    @Test fun passingByPlaceOrGpsGapCannotClaimWork() {
        val events=listOf(e("ARRIVAL",1000,"site"),e("TRIP_START",2000),e("TRIP_STOP",3000))
        assertNull(GpsActivitySuggestionRules.latest(events,mapOf("site" to GpsActivitySuggestionRules.Kind.WORKSITE),4000))
        val withGap=listOf(e("ARRIVAL",1000,"site"),e("GPS_GAP",2000),e("TRIP_STOP",3000))
        assertNull(GpsActivitySuggestionRules.latest(withGap,mapOf("site" to GpsActivitySuggestionRules.Kind.WORKSITE),4000))
    }
    @Test fun expiredAndDepartedHintsDisappear() {
        val events=listOf(e("ARRIVAL",1000,"site"),e("TRIP_STOP",2000))
        val kinds=mapOf("site" to GpsActivitySuggestionRules.Kind.WORKSITE)
        assertNull(GpsActivitySuggestionRules.latest(events,kinds,1_300_001))
        assertNull(GpsActivitySuggestionRules.latest(events+e("DEPARTURE",3000),kinds,4000))
    }
}
