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
}
