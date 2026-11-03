package com.r3gate.app

/** User-facing guidance; these checks do not claim to prove background tracking works. */
object GpsDeviceDiagnostics {
    data class Snapshot(
        val permissionGranted:Boolean,
        val locationEnabled:Boolean,
        val batteryUnrestricted:Boolean,
        val trackingRequested:Boolean,
        val serviceReportedActive:Boolean
    )
    enum class Issue { LOCATION_PERMISSION, LOCATION_SWITCH, BATTERY_RESTRICTIONS, SERVICE_NOT_ACTIVE }
    fun issues(s:Snapshot):List<Issue> {
        if(!s.trackingRequested) return emptyList()
        val result=mutableListOf<Issue>()
        if(!s.permissionGranted) result.add(Issue.LOCATION_PERMISSION)
        if(!s.locationEnabled) result.add(Issue.LOCATION_SWITCH)
        if(!s.batteryUnrestricted) result.add(Issue.BATTERY_RESTRICTIONS)
        if(!s.serviceReportedActive) result.add(Issue.SERVICE_NOT_ACTIVE)
        return result
    }
}
