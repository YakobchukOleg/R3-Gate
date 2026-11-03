package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsShopConfirmationRulesTest {
    private fun e(type:String,at:Long)=GpsShiftEstimate.Event(type,at)
    private val events=listOf(e("TRIP_START",100),e("TRIP_STOP",200))
    @Test fun allowsFreshUnknownTripAtTaggedShop() {
        assertTrue(GpsShopConfirmationRules.canConfirm(events,100,200,true,"UNKNOWN",300))
    }
    @Test fun neverOverwritesExplicitCategoryOrShopTag() {
        assertFalse(GpsShopConfirmationRules.canConfirm(events,100,200,true,"PRIVATE",300))
        assertFalse(GpsShopConfirmationRules.canConfirm(events,100,200,false,"UNKNOWN",300))
    }
    @Test fun rejectsChangedTripAndDuplicateStopEvidence() {
        assertFalse(GpsShopConfirmationRules.canConfirm(events,101,200,true,"UNKNOWN",300))
        assertFalse(GpsShopConfirmationRules.canConfirm(events+e("TRIP_STOP",200),100,200,true,"UNKNOWN",300))
    }
    @Test fun rejectsStalePromptAndNewMovementAfterStop() {
        assertFalse(GpsShopConfirmationRules.canConfirm(events,100,200,true,"UNKNOWN",1_300_201))
        assertFalse(GpsShopConfirmationRules.canConfirm(events+e("TRIP_START",250),100,200,true,"UNKNOWN",300))
        assertFalse(GpsShopConfirmationRules.canConfirm(events+e("GPS_GAP",250),100,200,true,"UNKNOWN",300))
    }
}
