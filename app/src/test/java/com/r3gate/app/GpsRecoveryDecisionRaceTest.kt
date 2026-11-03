package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsRecoveryDecisionRaceTest {
    private val d=GpsRecoveryMonitorRules.Decision
    @Test fun unchangedStalledWarningCanPublish() {
        assertTrue(GpsRecoveryAlertGuard.matchingDecision(d.ALERT,d.ALERT))
    }
    @Test fun unchangedMissingFixCanPublish() {
        assertTrue(GpsRecoveryAlertGuard.matchingDecision(d.ALERT_NO_FIX,d.ALERT_NO_FIX))
    }
    @Test fun unchangedPermissionIssueCanPublish() {
        assertTrue(GpsRecoveryAlertGuard.matchingDecision(d.ALERT_PERMISSION,d.ALERT_PERMISSION))
    }
    @Test fun unchangedProviderOffCanPublish() {
        assertTrue(GpsRecoveryAlertGuard.matchingDecision(d.ALERT_PROVIDER_OFF,d.ALERT_PROVIDER_OFF))
    }
    @Test fun unchangedIncompatibleProviderCanPublish() {
        assertTrue(GpsRecoveryAlertGuard.matchingDecision(d.ALERT_PROVIDER_UNUSABLE,d.ALERT_PROVIDER_UNUSABLE))
    }
    @Test fun newFixInvalidatesNoFixWarning() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(d.ALERT_NO_FIX,d.HEALTHY))
    }
    @Test fun newHeartbeatInvalidatesServiceStall() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(d.ALERT,d.HEALTHY))
    }
    @Test fun disabledTrackingInvalidatesWarning() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(d.ALERT,d.IGNORE))
    }
    @Test fun changedOutageCauseInvalidatesOldAlert() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(d.ALERT_NO_FIX,d.ALERT_PERMISSION))
    }
    @Test fun restoredProviderInvalidatesOldProviderOff() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(d.ALERT_PROVIDER_OFF,d.HEALTHY))
    }
    @Test fun healthyNeverProducesAlert() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(d.HEALTHY,d.HEALTHY))
    }
    @Test fun ignoredNeverProducesAlert() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(d.IGNORE,d.IGNORE))
    }
}
