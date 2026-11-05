package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsRecoveryMonitorRulesTest {
    private val r=GpsRecoveryMonitorRules
    private fun check(now:Long=1_000_000L,enabled:Boolean=true,
                      heartbeat:Long=100_000L,started:Long=100_000L,
                      alerted:Long=0L,lastFix:Long=heartbeat) =
        r.decide(now,enabled,heartbeat,started,alerted,lastFix)

    @Test fun stalledServiceNeedsUserAlert() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,check())
    }
    @Test fun disabledGpsNeverAlerts() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,check(enabled=false))
    }
    @Test fun negativeClockNeverAlerts() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,check(now=-100L))
    }
    @Test fun zeroClockNeverAlerts() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,check(now=0L))
    }
    @Test fun recentHeartbeatMeansHealthy() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,check(heartbeat=990_000L))
    }
    @Test fun heartbeatAtStaleBoundaryTriggersAlert() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,check(now=600_000L,heartbeat=120_000L,started=120_000L))
    }
    @Test fun oneMillisecondBeforeStaleIsHealthy() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,check(now=599_999L,heartbeat=120_000L,started=120_000L))
    }
    @Test fun freshSessionSupersedesOldHeartbeat() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,check(started=990_000L,heartbeat=200_000L))
    }
    @Test fun noHeartbeatUsesSessionStart() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,check(heartbeat=0L,started=100_000L))
    }
    @Test fun noHeartbeatInFreshSessionIsHealthy() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,check(heartbeat=0L,started=990_000L))
    }
    @Test fun noBaselineNeverInventsOutage() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,check(heartbeat=0L,started=0L))
    }
    @Test fun futureStartTimestampDoesNotAlert() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,check(started=2_000_000L))
    }
    @Test fun alertCooldownPreventsRepeatedNotification() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,check(alerted=900_000L))
    }
    @Test fun alertAtCooldownBoundaryAllowed() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,check(now=4_700_000L,alerted=1_100_000L))
    }
    @Test fun notificationJustBeforeCooldownIsSuppressed() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,check(now=4_699_999L,alerted=1_100_000L))
    }
    @Test fun futureAlertTimeDoesNotSilenceRealOutageAfterClockCorrection() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,check(alerted=2_000_000L))
    }
    @Test fun healthyStreamSuppressesNotificationEvenWithoutPreviousAlert() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,check(heartbeat=900_000L,alerted=0L))
    }
}
