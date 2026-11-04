package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsRecoveryClockRollbackCooldownTest {
    private val now=5_000_000L
    private val session=100_000L
    private fun decide(alertAt:Long,heartbeat:Long=100_000L,
                       fix:Long=100_000L,enabled:Boolean=true) =
        GpsRecoveryMonitorRules.decide(now,enabled,heartbeat,
            session,alertAt,fix)

    @Test fun futureAlertTimestampAfterClockCorrectionDoesNotHideStall() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,decide(now+10_000L))
    }
    @Test fun veryLargeFutureTimestampDoesNotSuppress() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,decide(Long.MAX_VALUE))
    }
    @Test fun exactlyCurrentTimestampDoesSuppress() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,decide(now))
    }
    @Test fun recentPreviousAlertStillSuppresses() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,decide(now-1_000L))
    }
    @Test fun cooldownOneMsBeforeDeadlineSuppresses() {
        val at=now-GpsRecoveryMonitorRules.NOTICE_COOLDOWN_MS+1
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,decide(at))
    }
    @Test fun cooldownAtDeadlineAllowsAlert() {
        val at=now-GpsRecoveryMonitorRules.NOTICE_COOLDOWN_MS
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,decide(at))
    }
    @Test fun zeroAlertTimestampDoesNotSuppress() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,decide(0L))
    }
    @Test fun negativeAlertTimestampDoesNotSuppress() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,decide(-42L))
    }
    @Test fun futureAlertTimestampDoesNotHideMissingFix() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,
            decide(now+10_000L,heartbeat=now,fix=session))
    }
    @Test fun futureAlertTimestampDoesNotOverrideHealthyReading() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,
            decide(now+10_000L,heartbeat=now,fix=now))
    }
    @Test fun disabledTrackingStillIgnored() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            decide(Long.MAX_VALUE,enabled=false))
    }
    @Test fun freshReminderStillSuppressesMissingFix() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            decide(now-1_000L,heartbeat=now,fix=session))
    }
}
