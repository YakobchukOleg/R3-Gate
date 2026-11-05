package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsRecoveryProviderCompatibilityTest {
    private fun evaluate(
        now:Long=1_000_000L,
        enabled:Boolean=true,
        heartbeat:Long=990_000L,
        started:Long=100_000L,
        alerted:Long=0L,
        fix:Long=990_000L,
        permission:Boolean=true,
        providerAvailable:Boolean=true,
        usable:Boolean=false
    )=GpsRecoveryMonitorRules.decide(
        now,enabled,heartbeat,started,alerted,fix,permission,providerAvailable,usable)

    @Test fun approximateOnlyAndGpsOnlyIsIncompatible() {
        assertNull(GpsProviderRules.permitted(true,false,false,true))
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_UNUSABLE,evaluate())
    }
    @Test fun approximateOnlyWithNetworkIsUsable() {
        assertEquals(GpsProviderRules.Provider.NETWORK,
            GpsProviderRules.permitted(true,true,false,true))
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,evaluate(usable=true))
    }
    @Test fun precisePermissionMakesGpsUsable() {
        assertEquals(GpsProviderRules.Provider.GPS,
            GpsProviderRules.permitted(true,false,true,false))
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,evaluate(usable=true))
    }
    @Test fun genuinelyDisabledProvidersAreReportedSeparately() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF,
            evaluate(providerAvailable=false))
    }
    @Test fun missingLocationPermissionTakesPrecedence() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION,
            evaluate(permission=false))
    }
    @Test fun stoppedServiceNotMistakenForUnusableProviderIfProviderWorks() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,
            evaluate(heartbeat=100_000L,usable=true))
    }
    @Test fun missingFixNotMistakenForUnusableProviderIfProviderWorks() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,
            evaluate(fix=100_000L,usable=true))
    }
    @Test fun incompatibleProviderOverridesOldFixIfProviderUnusable() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_UNUSABLE,
            evaluate(fix=100_000L))
    }
    @Test fun disabledTrackingSuppressesCompatibilityAlert() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,evaluate(enabled=false))
    }
    @Test fun newlyStartedSessionCanWarnWhenNoCompatibleProvider() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_UNUSABLE,
            evaluate(started=999_000L))
    }
    @Test fun noSessionSuppressesCompatibilityAlert() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,evaluate(started=0L))
    }
    @Test fun alertCooldownSuppressesCompatibilityAlert() {
        assertEquals(GpsRecoveryMonitorRules.Decision.IGNORE,evaluate(alerted=999_000L))
    }
    @Test fun compatibilityAlertAllowedAtCooldownBoundary() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_UNUSABLE,
            evaluate(now=4_700_000L,alerted=1_100_000L))
    }
    @Test fun compatibilityProblemCanBeClearedByTrustedFix() {
        assertTrue(GpsRecoveryMonitorRules.recoveredByTrustedFix("provider_unusable"))
    }
    @Test fun unknownProblemsAreNotRemovedByRecoveryCleanup() {
        assertFalse(GpsRecoveryMonitorRules.recoveredByTrustedFix("provider_unusable_extra"))
    }
}
