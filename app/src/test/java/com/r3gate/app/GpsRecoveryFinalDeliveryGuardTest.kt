package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsRecoveryFinalDeliveryGuardTest {
    private val alert=GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX
    private fun allowed(
        stopped:Boolean=false,
        original:GpsRecoveryMonitorRules.Decision=alert,
        latest:GpsRecoveryMonitorRules.Decision=alert,
        checked:Long=100_000L,
        current:Long=100_000L,
        tracking:Boolean=true,
        now:Long=1_000_000L
    )=GpsRecoveryAlertGuard.mayDeliver(
        stopped,original,latest,checked,current,tracking,now)

    @Test fun unchangedActiveOutageCanNotify() { assertTrue(allowed()) }
    @Test fun cancelledWorkerCannotNotify() { assertFalse(allowed(stopped=true)) }
    @Test fun trackingSwitchedOffCannotNotify() { assertFalse(allowed(tracking=false)) }
    @Test fun restartedServiceCannotReceiveOldWarning() {
        assertFalse(allowed(current=200_000L))
    }
    @Test fun freshGpsFixSuppressesLateAlert() {
        assertFalse(allowed(latest=GpsRecoveryMonitorRules.Decision.HEALTHY))
    }
    @Test fun changedFailureReasonSuppressesOutdatedMessage() {
        assertFalse(allowed(latest=GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION))
    }
    @Test fun invalidSessionSuppressesAlert() {
        assertFalse(allowed(checked=0L,current=0L))
    }
    @Test fun futureSessionSuppressesAlert() {
        assertFalse(allowed(checked=2_000_000L,current=2_000_000L))
    }
    @Test fun ignoredDecisionCannotNotify() {
        assertFalse(allowed(original=GpsRecoveryMonitorRules.Decision.IGNORE,
            latest=GpsRecoveryMonitorRules.Decision.IGNORE))
    }
    @Test fun validPermissionOutageCanNotify() {
        assertTrue(allowed(original=GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION,
            latest=GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION))
    }
}
