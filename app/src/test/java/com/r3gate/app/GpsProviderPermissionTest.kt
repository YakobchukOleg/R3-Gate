package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsProviderPermissionTest {
    private val p=GpsProviderRules
    @Test fun fineLocationSelectsGpsWhenBothProvidersAvailable() {
        assertEquals(GpsProviderRules.Provider.GPS,p.permitted(true,true,true,true))
    }
    @Test fun finePermissionWithoutCoarseStillAllowsGps() {
        assertEquals(GpsProviderRules.Provider.GPS,p.permitted(true,true,true,false))
    }
    @Test fun approximatePermissionSelectsNetworkInsteadOfGps() {
        assertEquals(GpsProviderRules.Provider.NETWORK,p.permitted(true,true,false,true))
    }
    @Test fun approximatePermissionWithOnlyNetworkSelectsNetwork() {
        assertEquals(GpsProviderRules.Provider.NETWORK,p.permitted(false,true,false,true))
    }
    @Test fun approximatePermissionWithGpsOnlyDoesNotAttemptGps() {
        assertNull(p.permitted(true,false,false,true))
    }
    @Test fun neitherPermissionBlocksProviders() {
        assertNull(p.permitted(true,true,false,false))
    }
    @Test fun finePermissionUsesNetworkWhenGpsUnavailable() {
        assertEquals(GpsProviderRules.Provider.NETWORK,p.permitted(false,true,true,false))
    }
    @Test fun finePermissionWithNoProviderDoesNotSubscribe() {
        assertNull(p.permitted(false,false,true,true))
    }
    @Test fun approximatePermissionWithNoProviderDoesNotSubscribe() {
        assertNull(p.permitted(false,false,false,true))
    }
    @Test fun precisePermissionUsesGpsIfNetworkUnavailable() {
        assertEquals(GpsProviderRules.Provider.GPS,p.permitted(true,false,true,true))
    }
    @Test fun restoringFinePermissionCanSwitchNetworkToGps() {
        val before=p.permitted(true,true,false,true)
        val after=p.permitted(true,true,true,true)
        assertTrue(p.shouldResubscribe(before,after))
    }
    @Test fun downgradingToApproximateSwitchesGpsToNetwork() {
        val before=p.permitted(true,true,true,true)
        val after=p.permitted(true,true,false,true)
        assertTrue(p.shouldResubscribe(before,after))
    }
    @Test fun unchangedApproximatePermissionAvoidsResubscription() {
        val before=p.permitted(true,true,false,true)
        val after=p.permitted(true,true,false,true)
        assertFalse(p.shouldResubscribe(before,after))
    }
    @Test fun disabledNetworkWithApproximatePermissionCannotClaimGpsSubscription() {
        val provider=p.permitted(true,false,false,true)
        assertNull(provider)
        assertFalse(p.shouldResubscribe(GpsProviderRules.Provider.NETWORK,provider))
    }
}
