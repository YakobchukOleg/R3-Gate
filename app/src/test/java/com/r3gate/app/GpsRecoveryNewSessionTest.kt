package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsRecoveryNewSessionTest {
    private fun decision(started:Long,lastAlert:Long,heartbeat:Long,fix:Long) =
        GpsRecoveryMonitorRules.decide(2_000_000L,true,heartbeat,started,lastAlert,fix)
    @Test fun restartingGpsMustResetOldCooldown() {
        assertTrue(GpsSessionBoundaryRules.resetRecoveryAlertForNewSession(true))
    }
    @Test fun duplicateStartWithinRunningSessionMustNotResetCooldown() {
        assertFalse(GpsSessionBoundaryRules.resetRecoveryAlertForNewSession(false))
    }
    @Test fun oldCooldownWouldSilenceNewOutage() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            decision(1_100_000L,1_990_000L,1_100_000L,1_100_000L))
    }
    @Test fun newSessionWithoutOldCooldownAlertsWhenServiceIsStale() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,
            decision(1_100_000L,0L,1_100_000L,1_100_000L))
    }
    @Test fun newSessionWithoutOldCooldownAlertsWhenFixIsMissing() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,
            decision(1_100_000L,0L,1_999_999L,1_100_000L))
    }
    @Test fun healthyFreshSessionStillDoesNotAlert() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,
            decision(1_990_000L,0L,1_999_999L,1_999_999L))
    }
    @Test fun brandNewSessionIsNotPrematurelyDeclaredStale() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,
            decision(1_999_000L,0L,1_999_000L,1_999_000L))
    }
    @Test fun oldSessionWarningDoesNotOverrideNewTrackingSession() {
        assertFalse(GpsRecoveryAlertGuard.stillRelevant(
            1_100_000L,1_999_000L,true,2_000_000L))
    }
    @Test fun sameSessionStillAllowsRelevantWarning() {
        assertTrue(GpsRecoveryAlertGuard.stillRelevant(
            1_999_000L,1_999_000L,true,2_000_000L))
    }
    @Test fun manuallyStoppedGpsStillCannotAlertAfterReset() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            GpsRecoveryMonitorRules.decide(2_000_000L,false,1_100_000L,
                1_100_000L,0L,1_100_000L))
    }
}
