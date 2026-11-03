package com.r3gate.app

/** Decide which Android location provider should be observed after availability changes. */
object GpsProviderRules {
    enum class Provider { GPS, NETWORK }
    fun preferred(gpsAvailable:Boolean,networkAvailable:Boolean):Provider? =
        when { gpsAvailable -> Provider.GPS; networkAvailable -> Provider.NETWORK; else -> null }
    /** GPS_PROVIDER needs precise location. NETWORK_PROVIDER can use approximate. */
    fun permitted(
        gpsAvailable:Boolean,
        networkAvailable:Boolean,
        fineGranted:Boolean,
        coarseGranted:Boolean
    ):Provider? = when {
        fineGranted && gpsAvailable -> Provider.GPS
        (fineGranted || coarseGranted) && networkAvailable -> Provider.NETWORK
        else -> null
    }
    /** Explain why registration cannot proceed, without mistaking coarse-only access for disabled GPS. */
    enum class AvailabilityProblem { PERMISSION, LOCATION_OFF, INCOMPATIBLE_PROVIDER }

    fun availabilityProblem(gpsAvailable:Boolean,networkAvailable:Boolean,
                            fineGranted:Boolean,coarseGranted:Boolean):AvailabilityProblem? = when {
        !fineGranted && !coarseGranted -> AvailabilityProblem.PERMISSION
        !gpsAvailable && !networkAvailable -> AvailabilityProblem.LOCATION_OFF
        permitted(gpsAvailable,networkAvailable,fineGranted,coarseGranted)==null ->
            AvailabilityProblem.INCOMPATIBLE_PROVIDER
        else -> null
    }

    fun shouldResubscribe(current:Provider?,preferred:Provider?):Boolean =
        preferred!=null && current!=preferred
}
