package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsRecoveryDecisionRaceTest {
    @Test fun unchangedStalledWarningCanPublish() {
        assertTrue(GpsRecoveryAlertGuard.matchingDecision(GpsRecoveryMonitorRules.Decision.ALERT,GpsRecoveryMonitorRules.Decision.ALERT))
    }
    @Test fun unchangedMissingFixCanPublish() {
        assertTrue(GpsRecoveryAlertGuard.matchingDecision(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX))
    }
    @Test fun unchangedPermissionIssueCanPublish() {
        assertTrue(GpsRecoveryAlertGuard.matchingDecision(GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION,GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION))
    }
    @Test fun unchangedProviderOffCanPublish() {
        assertTrue(GpsRecoveryAlertGuard.matchingDecision(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF,GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF))
    }
    @Test fun unchangedIncompatibleProviderCanPublish() {
        assertTrue(GpsRecoveryAlertGuard.matchingDecision(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_UNUSABLE,GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_UNUSABLE))
    }
    @Test fun newFixInvalidatesNoFixWarning() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,GpsRecoveryMonitorRules.Decision.HEALTHY))
    }
    @Test fun newHeartbeatInvalidatesServiceStall() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(GpsRecoveryMonitorRules.Decision.ALERT,GpsRecoveryMonitorRules.Decision.HEALTHY))
    }
    @Test fun disabledTrackingInvalidatesWarning() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(GpsRecoveryMonitorRules.Decision.ALERT,GpsRecoveryMonitorRules.Decision.IGNORE))
    }
    @Test fun changedOutageCauseInvalidatesOldAlert() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION))
    }
    @Test fun restoredProviderInvalidatesOldProviderOff() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF,GpsRecoveryMonitorRules.Decision.HEALTHY))
    }
    @Test fun healthyNeverProducesAlert() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(GpsRecoveryMonitorRules.Decision.HEALTHY,GpsRecoveryMonitorRules.Decision.HEALTHY))
    }
    @Test fun ignoredNeverProducesAlert() {
        assertFalse(GpsRecoveryAlertGuard.matchingDecision(GpsRecoveryMonitorRules.Decision.IGNORE,GpsRecoveryMonitorRules.Decision.IGNORE))
    }
}
