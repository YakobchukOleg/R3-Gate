package com.r3gate.app

/** Reject delayed callbacks from a previously subscribed location provider. */
object GpsFixSourceRules {
    fun accept(activeProvider:String?,fixProvider:String?,trackingEnabled:Boolean):Boolean =
        trackingEnabled && !activeProvider.isNullOrBlank() && activeProvider==fixProvider
}
