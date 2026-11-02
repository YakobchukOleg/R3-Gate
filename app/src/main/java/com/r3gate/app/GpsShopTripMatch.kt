package com.r3gate.app

/**
 * Matches an explicitly tagged shop stop to one completed GPS trip.
 * Never infers a shopping purpose or changes a trip's saved category.
 */
object GpsShopTripMatch {
    fun matchingTrip(
        events:List<GpsShiftEstimate.Event>,
        stopMs:Long,
        shopTagged:Boolean
    ):GpsTripBilling.Trip? {
        if(!shopTagged || stopMs<=0) return null
        return GpsTripBilling.pair(events,emptyMap())
            .singleOrNull { it.endMs==stopMs && it.startMs<stopMs }
    }
}
