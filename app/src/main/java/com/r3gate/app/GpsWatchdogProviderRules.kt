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

    /** An updateSubscription() call can return without registering if Android providers changed. */
    fun registrationSucceeded(selectedProvider:String?,subscribed:Boolean,
                              currentProvider:String?):Boolean =
        status(!selectedProvider.isNullOrBlank(),subscribed,currentProvider,selectedProvider)==Status.ACTIVE

    /** A requested sampling cadence is applied only after a matching live subscription. */
    fun cadenceApplied(selectedProvider:String?,subscribed:Boolean,
                       currentProvider:String?,requestedMs:Long,registeredMs:Long):Boolean =
        requestedMs>0L && registeredMs==requestedMs &&
            registrationSucceeded(selectedProvider,subscribed,currentProvider)

    fun shouldReacquire(
        status:Status,
        noFixProbe:Boolean,
        nowMs:Long,
        lastAttemptMs:Long
    ):Boolean {
        if(status==Status.NO_PROVIDER || nowMs<=0L ||
            lastAttemptMs>nowMs &&
                lastAttemptMs-nowMs<GpsWatchdogRules.CLOCK_ROLLBACK_RESET_MS ||
            lastAttemptMs>0L && lastAttemptMs<=nowMs &&
                nowMs-lastAttemptMs<GpsWatchdogRules.RETRY_COOLDOWN_MS)
            return false
        return status==Status.NEEDS_SUBSCRIPTION ||
            status==Status.ACTIVE && noFixProbe
    }
}
