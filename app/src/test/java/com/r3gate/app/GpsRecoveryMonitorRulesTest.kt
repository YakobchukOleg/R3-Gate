package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsRecoveryMonitorRulesTest {
    private val r=GpsRecoveryMonitorRules
    private fun check(now:Long=1_000_000L,enabled:Boolean=true,
                      heartbeat:Long=100_000L,started:Long=100_000L,
                      alerted:Long=0L) =
        r.decide(now,enabled,heartbeat,started,alerted)

    @Test fun stalledServiceNeedsUserAlert() {
        assertEquals(r.Decision.ALERT,check())
    }
    @Test fun disabledGpsNeverAlerts() {
        assertEquals(r.Decision.IGNORE,check(enabled=false))
    }
    @Test fun negativeClockNeverAlerts() {
        assertEquals(r.Decision.IGNORE,check(now=-100L))
    }
    @Test fun zeroClockNeverAlerts() {
        assertEquals(r.Decision.IGNORE,check(now=0L))
    }
    @Test fun recentHeartbeatMeansHealthy() {
        assertEquals(r.Decision.HEALTHY,check(heartbeat=990_000L))
    }
    @Test fun heartbeatAtStaleBoundaryTriggersAlert() {
        assertEquals(r.Decision.ALERT,check(now=600_000L,heartbeat=120_000L,started=120_000L))
    }
    @Test fun oneMillisecondBeforeStaleIsHealthy() {
        assertEquals(r.Decision.HEALTHY,check(now=599_999L,heartbeat=120_000L,started=120_000L))
    }
    @Test fun freshSessionSupersedesOldHeartbeat() {
        assertEquals(r.Decision.HEALTHY,check(started=990_000L,heartbeat=200_000L))
    }
    @Test fun noHeartbeatUsesSessionStart() {
        assertEquals(r.Decision.ALERT,check(heartbeat=0L,started=100_000L))
    }
    @Test fun noHeartbeatInFreshSessionIsHealthy() {
        assertEquals(r.Decision.HEALTHY,check(heartbeat=0L,started=990_000L))
    }
    @Test fun noBaselineNeverInventsOutage() {
        assertEquals(r.Decision.IGNORE,check(heartbeat=0L,started=0L))
    }
    @Test fun futureStartTimestampDoesNotAlert() {
        assertEquals(r.Decision.IGNORE,check(started=2_000_000L))
    }
    @Test fun alertCooldownPreventsRepeatedNotification() {
        assertEquals(r.Decision.IGNORE,check(alerted=900_000L))
    }
    @Test fun alertAtCooldownBoundaryAllowed() {
        assertEquals(r.Decision.ALERT,check(now=4_700_000L,alerted=1_100_000L))
    }
    @Test fun notificationJustBeforeCooldownIsSuppressed() {
        assertEquals(r.Decision.IGNORE,check(now=4_699_999L,alerted=1_100_000L))
    }
    @Test fun futureAlertTimeSuppressesNotification() {
        assertEquals(r.Decision.IGNORE,check(alerted=2_000_000L))
    }
    @Test fun healthyStreamSuppressesNotificationEvenWithoutPreviousAlert() {
        assertEquals(r.Decision.HEALTHY,check(heartbeat=900_000L,alerted=0L))
    }
}
