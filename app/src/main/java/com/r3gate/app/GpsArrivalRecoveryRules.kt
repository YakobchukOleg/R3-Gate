package com.r3gate.app

/**
 * After a coverage gap retain the last confirmed geofence identity as context,
 * not as evidence that the user stayed there during the outage.
 * Clear the candidate and fix timestamps: any departure still requires fresh
 * independent GPS fixes and the standard dwell rule.
 */
object GpsArrivalRecoveryRules {
    fun afterCoverageGap(previous:GpsArrivalRules.State):GpsArrivalRules.State =
        GpsArrivalRules.State(confirmedId=previous.confirmedId)
}
