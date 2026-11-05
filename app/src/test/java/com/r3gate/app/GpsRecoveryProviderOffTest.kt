package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsRecoveryProviderOffTest {
    private fun decision(
        now:Long=1_000_000L,
        enabled:Boolean=true,
        heartbeat:Long=990_000L,
        started:Long=100_000L,
        alerted:Long=0L,
        fix:Long=990_000L,
        permission:Boolean=true,
        provider:Boolean=false
    )=GpsRecoveryMonitorRules.decide(
        now,enabled,heartbeat,started,alerted,fix,permission,provider)

    @Test fun disabledAndroidLocationIsReported() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF,decision())
    }
    @Test fun providerOffOverridesRecentFixAndHeartbeat() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF,
            decision(heartbeat=999_999L,fix=999_999L))
    }
    @Test fun missingPermissionTakesPrecedence() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION,
            decision(permission=false))
    }
    @Test fun enabledLocationWithFreshDataIsHealthy() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,decision(provider=true))
    }
    @Test fun enabledProviderWithLostFixUsesExistingWarning() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,
            decision(provider=true,fix=100_000L))
    }
    @Test fun providerOffAndStoppedServiceStillExplainProvider() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF,
            decision(heartbeat=100_000L))
    }
    @Test fun disabledWorkMeterGpsDoesNotWarn() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            decision(enabled=false))
    }
    @Test fun uninitializedSessionDoesNotWarn() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            decision(started=0L))
    }
    @Test fun futureSessionStartDoesNotWarn() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            decision(started=1_000_001L))
    }
    @Test fun clockRollbackDoesNotWarn() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            decision(now=50_000L))
    }
    @Test fun repeatedProviderOffAlertRespectsCooldown() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            decision(alerted=990_000L))
    }
    @Test fun providerOffCanAlertAfterCooldown() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF,
            decision(now=4_700_000L,alerted=1_100_000L))
    }
    @Test fun futureAlertCheckpointDoesNotHideProviderOffAfterClockCorrection() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF,
            decision(alerted=1_010_000L))
    }
    @Test fun recoveryWithFreshFixClearsProviderOffState() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,
            decision(provider=true,heartbeat=990_000L,fix=990_000L))
    }
}
