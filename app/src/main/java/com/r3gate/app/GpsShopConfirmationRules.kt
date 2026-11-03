package com.r3gate.app

/** Final, fail-closed check before explicitly tagging a GPS trip as shopping. */
object GpsShopConfirmationRules {
    fun canConfirm(
        events:List<GpsShiftEstimate.Event>,
        startMs:Long,
        stopMs:Long,
        shopStillTagged:Boolean,
        savedCategory:String,
        nowMs:Long
    ):Boolean {
        if(nowMs<stopMs || stopMs<=startMs || savedCategory!="UNKNOWN") return false
        if(nowMs-stopMs>GpsActivitySuggestionRules.MAX_HINT_AGE_MS) return false
        val match=GpsShopTripMatch.matchingTrip(events,stopMs,shopStillTagged) ?: return false
        if(match.startMs!=startMs) return false
        return events.none {
            it.timestampMs>stopMs && it.timestampMs<=nowMs &&
                it.type in setOf("TRIP_START","GPS_GAP")
        }
    }
}
