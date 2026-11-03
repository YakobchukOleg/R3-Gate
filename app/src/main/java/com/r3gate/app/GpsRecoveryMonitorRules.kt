package com.r3gate.app

/**
 * External monitor can survive a killed service process through WorkManager.
 * It only reports an outage: Android 12+ restricts background FGS starts.
 */
object GpsRecoveryMonitorRules {
    const val STALE_HEARTBEAT_MS=8*60_000L
    const val NOTICE_COOLDOWN_MS=60*60_000L
    enum class Decision { IGNORE, HEALTHY, ALERT }
    fun decide(
        nowMs:Long,
        desiredTracking:Boolean,
        lastHeartbeatMs:Long,
        startedMs:Long,
        lastAlertMs:Long
    ):Decision {
        if(!desiredTracking || nowMs<=0L) return Decision.IGNORE
        val baseline=if(lastHeartbeatMs in 1..nowMs) lastHeartbeatMs else startedMs
        if(baseline<=0L || baseline>nowMs) return Decision.IGNORE
        if(nowMs-baseline<STALE_HEARTBEAT_MS) return Decision.HEALTHY
        if(lastAlertMs>nowMs || lastAlertMs>0L && nowMs-lastAlertMs<NOTICE_COOLDOWN_MS)
            return Decision.IGNORE
        return Decision.ALERT
    }
}
