package com.r3gate.app

/**
 * A failed LocationManager registration leaves no active subscription.
 * Retry against available Android providers rather than trusting the last
 * successful subscription flag.
 */
object GpsWatchdogProviderRules {
    enum class Status { NO_PROVIDER, ACTIVE, NEEDS_SUBSCRIPTION }
    fun status(providerAvailable:Boolean,subscribed:Boolean,currentProvider:String?,
               selectedProvider:String?):Status = when {
        !providerAvailable || selectedProvider.isNullOrBlank() -> Status.NO_PROVIDER
        !subscribed || currentProvider!=selectedProvider -> Status.NEEDS_SUBSCRIPTION
        else -> Status.ACTIVE
    }

    fun shouldReacquire(status:Status,noFixProbe:Boolean):Boolean =
        status==Status.NEEDS_SUBSCRIPTION ||
            status==Status.ACTIVE && noFixProbe
}
