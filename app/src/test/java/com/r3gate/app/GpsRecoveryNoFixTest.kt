package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsRecoveryNoFixTest {
    private val rules=GpsRecoveryMonitorRules
    private fun assess(now:Long=1_000_000L, heartbeat:Long=990_000L,
        started:Long=100_000L, fix:Long=100_000L, alerted:Long=0L,
        tracking:Boolean=true) =
        rules.decide(now,tracking,heartbeat,started,alerted,fix)

    @Test fun liveServiceWithoutFixRaisesWarning() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,assess())
    }
    @Test fun recentlyUpdatedFixIsHealthy() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,assess(fix=990_000L))
    }
    @Test fun missingFixUsesCurrentSessionStart() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,assess(fix=0))
    }
    @Test fun freshSessionWithNoFixIsHealthy() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,
            assess(started=990_000L,fix=0L))
    }
    @Test fun previousSessionFixCannotHideMissingFix() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,
            assess(started=200_000L,fix=100_000L))
    }
    @Test fun fixJustBeforeTenMinuteBoundaryIsHealthy() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,
            assess(now=700_000L,started=100_000L,fix=100_001L,heartbeat=690_000L))
    }
    @Test fun fixAtTenMinuteBoundaryIsStale() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,
            assess(now=700_000L,started=100_000L,fix=100_000L,heartbeat=690_000L))
    }
    @Test fun killedServiceOutageTakesPrecedenceOverMissingFix() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,
            assess(heartbeat=100_000L))
    }
    @Test fun alertCooldownAppliesToNoFixWarning() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            assess(alerted=990_000L))
    }
    @Test fun noFixAlertAllowedAfterCooldown() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,
            assess(now=4_700_000L,started=100_000L,heartbeat=4_690_000L,
                fix=100_000L,alerted=1_100_000L))
    }
    @Test fun disabledTrackingDoesNotAlertForStaleFix() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            assess(tracking=false))
    }
    @Test fun futureFixCannotMaskMissingFix() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,
            assess(fix=2_000_000L))
    }
    @Test fun timestampRollbackSuppressesStaleWarning() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            assess(now=90_000L,started=100_000L,heartbeat=80_000L))
    }
    @Test fun recentFixWithOldHeartbeatStillReportsServiceOutage() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,
            assess(fix=990_000L,heartbeat=100_000L))
    }
}
