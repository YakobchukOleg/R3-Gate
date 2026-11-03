package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsProviderRulesTest {
    @Test fun gpsPreferredWhenBothAvailable() {
        assertEquals(GpsProviderRules.Provider.GPS,GpsProviderRules.preferred(true,true))
    }
    @Test fun fallsBackToNetworkWhenGpsDisabled() {
        assertEquals(GpsProviderRules.Provider.NETWORK,GpsProviderRules.preferred(false,true))
    }
    @Test fun noProviderDoesNotFakeActiveSubscription() {
        assertNull(GpsProviderRules.preferred(false,false))
        assertFalse(GpsProviderRules.shouldResubscribe(GpsProviderRules.Provider.GPS,null))
    }
    @Test fun unchangedProviderIsNotResubscribed() {
        assertFalse(GpsProviderRules.shouldResubscribe(GpsProviderRules.Provider.GPS,GpsProviderRules.Provider.GPS))
        assertTrue(GpsProviderRules.shouldResubscribe(GpsProviderRules.Provider.GPS,GpsProviderRules.Provider.NETWORK))
    }
}
