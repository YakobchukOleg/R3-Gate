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

    /** Removing an object invalidates its checkpoint without proving a departure. */
    fun afterPlacesChanged(
        previous: GpsArrivalRules.State,
        availablePlaces: List<GpsWorkRules.Place>
    ): GpsArrivalRules.State {
        val confirmed = previous.confirmedId
        if (confirmed != null && availablePlaces.none { it.id == confirmed })
            return GpsArrivalRules.State(lastFixMs = previous.lastFixMs)
        val candidate = previous.candidateId
        if (candidate != null && availablePlaces.none { it.id == candidate })
            return previous.copy(candidateId = null, candidateSinceMs = 0L)
        return previous
    }
}
