package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsSourceTransitionRulesTest {
    @Test fun changingGpsToNetworkInvalidatesMovementEvidence() {
        assertEquals(GpsSourceTransitionRules.Decision(true,true,true),
            GpsSourceTransitionRules.compare("gps","network"))
    }
    @Test fun changingBackAlsoInvalidatesMovementEvidence() {
        assertTrue(GpsSourceTransitionRules.compare("network","gps").resetMotion)
    }
    @Test fun repeatedFixesFromOneProviderKeepEvidence() {
        assertEquals(GpsSourceTransitionRules.Decision(false,false,false),
            GpsSourceTransitionRules.compare("gps","gps"))
    }
    @Test fun initialFixHasNoArtificialTransition() {
        assertFalse(GpsSourceTransitionRules.compare(null,"gps").providerChanged)
        assertFalse(GpsSourceTransitionRules.compare("","network").providerChanged)
    }
}
