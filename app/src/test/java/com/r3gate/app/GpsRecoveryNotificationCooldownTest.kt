package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsRecoveryNotificationCooldownTest {
    private fun sent(modern:Boolean,allowed:Boolean) =
        GpsRecoveryMonitorRules.shouldMarkAlertSent(modern,allowed)

    @Test fun android13WithPermissionCanMarkNotificationSent() {
        assertTrue(sent(true,true))
    }
    @Test fun android13WithoutPermissionCannotConsumeCooldown() {
        assertFalse(sent(true,false))
    }
    @Test fun olderAndroidDoesNotRequireRuntimeNotificationPermission() {
        assertTrue(sent(false,false))
    }
    @Test fun olderAndroidWithPermissionAlsoCanNotify() {
        assertTrue(sent(false,true))
    }
    @Test fun newlyGrantedPermissionCanNotifyAfterPreviousBlock() {
        assertFalse(sent(true,false))
        assertTrue(sent(true,true))
    }
    @Test fun revokedPermissionStopsCooldownConsumption() {
        assertTrue(sent(true,true))
        assertFalse(sent(true,false))
    }
    @Test fun firstBlockedNotificationLeavesAlertTimeAtZero() {
        val lastAlert=if(sent(true,false)) 1_000_000L else 0L
        assertEquals(0L,lastAlert)
    }
    @Test fun permittedNotificationCanRecordTimestamp() {
        val lastAlert=if(sent(true,true)) 1_000_000L else 0L
        assertEquals(1_000_000L,lastAlert)
    }
    @Test fun blockedNotificationAllowsNextEligibleAlert() {
        val now=2_000_000L
        val lastAlert=if(sent(true,false)) now else 0L
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,
            GpsRecoveryMonitorRules.decide(now,true,now-1_000L,100_000L,
                lastAlert,100_000L))
    }
    @Test fun permittedNotificationTriggersCooldown() {
        val now=2_000_000L
        val lastAlert=if(sent(true,true)) now else 0L
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,
            GpsRecoveryMonitorRules.decide(now+1_000L,true,now,100_000L,
                lastAlert,100_000L))
    }
}
