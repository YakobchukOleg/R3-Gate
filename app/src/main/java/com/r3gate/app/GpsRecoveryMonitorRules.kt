package com.r3gate.app

/**
 * Process-independent GPS health assessment. A living service is not sufficient
 * evidence that Android is still supplying location fixes.
 */
object GpsRecoveryMonitorRules {
    const val STALE_HEARTBEAT_MS=8*60_000L
    const val STALE_FIX_MS=10*60_000L
    const val NOTICE_COOLDOWN_MS=60*60_000L
    enum class Decision { IGNORE, HEALTHY, ALERT, ALERT_NO_FIX, ALERT_PERMISSION, ALERT_PROVIDER_OFF }
    fun decide(
        nowMs:Long,
        desiredTracking:Boolean,
        lastHeartbeatMs:Long,
        startedMs:Long,
        lastAlertMs:Long,
        lastFixMs:Long=0L,
        locationPermissionGranted:Boolean=true,
        locationProviderAvailable:Boolean=true
    ):Decision {
        if(!desiredTracking || nowMs<=0L) return Decision.IGNORE
        if(startedMs<=0L || startedMs>nowMs) return Decision.IGNORE
        // Do not borrow a heartbeat or fix from a previous service session.
        val heartbeat=maxOf(startedMs,
            if(lastHeartbeatMs in startedMs..nowMs) lastHeartbeatMs else 0L)
        val fix=maxOf(startedMs,
            if(lastFixMs in startedMs..nowMs) lastFixMs else 0L)
        val serviceStale=nowMs-heartbeat>=STALE_HEARTBEAT_MS
        val fixStale=nowMs-fix>=STALE_FIX_MS
        if(locationPermissionGranted && locationProviderAvailable && !serviceStale && !fixStale) return Decision.HEALTHY
        if(lastAlertMs>nowMs || lastAlertMs>0L && nowMs-lastAlertMs<NOTICE_COOLDOWN_MS)
            return Decision.IGNORE
        return when {
            !locationPermissionGranted -> Decision.ALERT_PERMISSION
            !locationProviderAvailable -> Decision.ALERT_PROVIDER_OFF
            serviceStale -> Decision.ALERT
            else -> Decision.ALERT_NO_FIX
        }
    }
}
