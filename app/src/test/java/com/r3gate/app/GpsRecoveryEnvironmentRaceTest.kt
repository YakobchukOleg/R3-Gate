package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsRecoveryEnvironmentRaceTest {
    private fun evaluate(
        permission:Boolean=true,available:Boolean=true,usable:Boolean=true,
        heartbeat:Long=100_000L,fix:Long=100_000L
    )=GpsRecoveryMonitorRules.decide(1_000_000L,true,heartbeat,
        100_000L,0L,fix,permission,available,usable)
    private fun compatible(before:GpsRecoveryMonitorRules.Decision,
                           after:GpsRecoveryMonitorRules.Decision)=
        GpsRecoveryAlertGuard.matchingDecision(before,after)

    @Test fun permissionRevocationInvalidatesOldStallDiagnosis() {
        assertFalse(compatible(evaluate(),evaluate(permission=false)))
    }
    @Test fun permissionGrantInvalidatesOldPermissionDiagnosis() {
        assertFalse(compatible(evaluate(permission=false),evaluate()))
    }
    @Test fun providerShutdownInvalidatesOldStallDiagnosis() {
        assertFalse(compatible(evaluate(),evaluate(available=false,usable=false)))
    }
    @Test fun providerRecoveryInvalidatesOldDisabledDiagnosis() {
        assertFalse(compatible(evaluate(available=false,usable=false),evaluate()))
    }
    @Test fun incompatibilityInvalidatesOldStallDiagnosis() {
        assertFalse(compatible(evaluate(),evaluate(usable=false)))
    }
    @Test fun precisePermissionRestoredInvalidatesIncompatibility() {
        assertFalse(compatible(evaluate(usable=false),evaluate()))
    }
    @Test fun fixedLocationInvalidatesPreviousOutage() {
        assertFalse(compatible(evaluate(),evaluate(heartbeat=990_000L,fix=990_000L)))
    }
    @Test fun unchangedUnusableProviderCanStillNotify() {
        val x=evaluate(usable=false)
        assertTrue(compatible(x,x))
    }
    @Test fun unchangedDisabledProviderCanStillNotify() {
        val x=evaluate(available=false,usable=false)
        assertTrue(compatible(x,x))
    }
    @Test fun changedCauseBetweenMissingFixAndServiceStallIsRejected() {
        val noFix=evaluate(heartbeat=990_000L)
        val stall=evaluate()
        assertFalse(compatible(noFix,stall))
    }
}
