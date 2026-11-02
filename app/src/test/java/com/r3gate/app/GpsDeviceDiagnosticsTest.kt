package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsDeviceDiagnosticsTest {
    @Test fun disabledTrackingDoesNotShowUnwantedWarnings() {
        val s=GpsDeviceDiagnostics.Snapshot(false,false,false,false,false)
        assertTrue(GpsDeviceDiagnostics.issues(s).isEmpty())
    }
    @Test fun eachUnavailableConditionIsReported() {
        val s=GpsDeviceDiagnostics.Snapshot(false,false,false,true,false)
        assertEquals(listOf(
            GpsDeviceDiagnostics.Issue.LOCATION_PERMISSION,
            GpsDeviceDiagnostics.Issue.LOCATION_SWITCH,
            GpsDeviceDiagnostics.Issue.BATTERY_RESTRICTIONS,
            GpsDeviceDiagnostics.Issue.SERVICE_NOT_ACTIVE
        ),GpsDeviceDiagnostics.issues(s))
    }
    @Test fun allAvailableConditionsHaveNoDiagnosticIssues() {
        val s=GpsDeviceDiagnostics.Snapshot(true,true,true,true,true)
        assertTrue(GpsDeviceDiagnostics.issues(s).isEmpty())
    }
    @Test fun permissionAndLocationDoNotImplyServiceIsRunning() {
        val s=GpsDeviceDiagnostics.Snapshot(true,true,true,true,false)
        assertEquals(listOf(GpsDeviceDiagnostics.Issue.SERVICE_NOT_ACTIVE),GpsDeviceDiagnostics.issues(s))
    }
}
