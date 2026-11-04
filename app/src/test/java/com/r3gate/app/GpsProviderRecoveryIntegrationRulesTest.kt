package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

/** Cross-check service and background monitor classifications for provider changes. */
class GpsProviderRecoveryIntegrationRulesTest {
    private fun decision(gps:Boolean,network:Boolean,fine:Boolean,coarse:Boolean,
                         heartbeat:Long=1_990_000L,fix:Long=1_990_000L) : GpsRecoveryMonitorRules.Decision {
        val issue=GpsProviderRules.availabilityProblem(gps,network,fine,coarse)
        return GpsRecoveryMonitorRules.decide(2_000_000L,true,heartbeat,1_000_000L,0L,fix,
            fine||coarse,gps||network,issue==null)
    }
    @Test fun approximateAndGpsOnlyExplainsIncompatibility() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_UNUSABLE,
            decision(true,false,false,true))
    }
    @Test fun preciseAndGpsOnlyIsHealthy() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,
            decision(true,false,true,true))
    }
    @Test fun coarseWithNetworkProviderIsHealthy() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,
            decision(false,true,false,true))
    }
    @Test fun coarseWithGpsAndNetworkIsHealthy() {
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,
            decision(true,true,false,true))
    }
    @Test fun noEnabledProvidersReportsLocationOff() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF,
            decision(false,false,true,true))
    }
    @Test fun deniedPermissionsHavePriorityOverProviderOff() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION,
            decision(false,false,false,false))
    }
    @Test fun deniedPermissionsHavePriorityOverProviderIncompatibility() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PERMISSION,
            decision(true,false,false,false))
    }
    @Test fun providerRecoveryDoesNotHideNoFixOutage() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,
            decision(false,true,false,true,fix=1_000_000L))
    }
    @Test fun providerRecoveryDoesNotHideDeadService() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT,
            decision(false,true,false,true,heartbeat=1_000_000L))
    }
    @Test fun restoringNetworkProviderResolvesIncompatibleCoarseAccess() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_UNUSABLE,
            decision(true,false,false,true))
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,
            decision(true,true,false,true))
    }
    @Test fun restoringPrecisePermissionResolvesIncompatibleGpsAccess() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_UNUSABLE,
            decision(true,false,false,true))
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,
            decision(true,false,true,true))
    }
    @Test fun restoringLocationServiceClearsProviderOffDiagnosis() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF,
            decision(false,false,true,true))
        assertEquals(GpsRecoveryMonitorRules.Decision.HEALTHY,
            decision(true,false,true,true))
    }
    @Test fun successfulProviderSelectionAloneIsNotEvidenceOfFreshFix() {
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_NO_FIX,
            decision(true,false,true,true,fix=1_000_000L))
    }
    @Test fun providerLossCannotInventTripOrArrivalEvidence() {
        assertEquals(GpsProviderRules.AvailabilityProblem.LOCATION_OFF,
            GpsProviderRules.availabilityProblem(false,false,true,true))
        assertEquals(GpsRecoveryMonitorRules.Decision.ALERT_PROVIDER_OFF,
            decision(false,false,true,true))
    }
}
