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
    fun shouldResubscribe(current:Provider?,preferred:Provider?):Boolean =
        preferred!=null && current!=preferred
}
