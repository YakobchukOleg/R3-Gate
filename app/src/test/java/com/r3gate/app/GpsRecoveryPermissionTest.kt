package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsRecoveryPermissionTest {
    private fun check(
        now:Long=1_000_000L,
        enabled:Boolean=true,
        heartbeat:Long=990_000L,
        started:Long=100_000L,
        alerted:Long=0L,
        fix:Long=990_000L,
        permission:Boolean=false
    )=GpsRecoveryMonitorRules.decide(now,enabled,heartbeat,started,alerted,fix,permission)

    @Test fun revokedPermissionTriggersDedicatedWarning() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION,check())
    }
    @Test fun missingPermissionOverridesFreshHeartbeat() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION,
            check(heartbeat=999_999L))
    }
    @Test fun missingPermissionOverridesHealthyFix() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION,
            check(fix=999_999L))
    }
    @Test fun missingPermissionOverridesStaleServiceReason() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION,
            check(heartbeat=100_000L,fix=100_000L))
    }
    @Test fun grantedPermissionAllowsHealthyState() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,check(permission=true))
    }
    @Test fun restoredPermissionAndNoFixAreDistinguished() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,
            check(permission=true,fix=100_000L))
    }
    @Test fun revokedPermissionWithTrackingDisabledIsIgnored() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,check(enabled=false))
    }
    @Test fun revokedPermissionWithoutAnySessionIsIgnored() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            check(started=0L))
    }
    @Test fun revokedPermissionWithFutureSessionIsIgnored() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            check(started=1_000_001L))
    }
    @Test fun revokedPermissionWithInvalidClockIsIgnored() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,check(now=0L))
    }
    @Test fun revokedPermissionAlertCooldownApplies() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            check(alerted=990_000L))
    }
    @Test fun revokedPermissionAlertsAtCooldownBoundary() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION,
            check(now=4_700_000L,alerted=1_100_000L))
    }
    @Test fun futureAlertTimestampAfterClockCorrectionDoesNotHideRevokedPermission() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION,
            check(alerted=1_000_001L))
    }
    @Test fun grantedPermissionAndDeadServiceStillAlerts() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,
            check(permission=true,heartbeat=100_000L))
    }
}
