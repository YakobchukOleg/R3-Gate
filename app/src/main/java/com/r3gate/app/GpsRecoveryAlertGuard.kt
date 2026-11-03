package com.r3gate.app

/**
 * Reject an outage result captured before tracking was disabled or restarted.
 * The Worker rechecks durable preferences immediately before publishing an alert.
 */
object GpsRecoveryAlertGuard {
    fun stillRelevant(
        checkedSessionMs:Long,
        currentSessionMs:Long,
        desiredTracking:Boolean,
        nowMs:Long
    ):Boolean =
        desiredTracking && checkedSessionMs>0L && checkedSessionMs<=nowMs &&
            currentSessionMs==checkedSessionMs
    /** Last defense before delivery, after Android has prepared the notification. */
    fun mayDeliver(
        workerStopped:Boolean,
        original:GpsRecoveryMonitorRules.Decision,
        latest:GpsRecoveryMonitorRules.Decision,
        checkedSessionMs:Long,
        currentSessionMs:Long,
        desiredTracking:Boolean,
        nowMs:Long
    ):Boolean =
        !workerStopped && stillRelevant(checkedSessionMs,currentSessionMs,
            desiredTracking,nowMs) && matchingDecision(original,latest)

    /** A fresh accepted fix or service heartbeat can invalidate a stale worker result. */
    fun matchingDecision(original:GpsRecoveryMonitorRules.Decision,
                         latest:GpsRecoveryMonitorRules.Decision):Boolean =
        original==latest && original!=GpsRecoveryMonitorRules.Decision.HEALTHY &&
            original!=GpsRecoveryMonitorRules.Decision.IGNORE
}

