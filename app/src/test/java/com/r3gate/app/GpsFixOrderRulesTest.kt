package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsFixOrderRulesTest {
    @Test fun acceptsFirstAndStrictlyNewerFixes() {
        assertTrue(GpsFixOrderRules.accept(1000,0))
        assertTrue(GpsFixOrderRules.accept(2000,1000))
    }
    @Test fun rejectsDelayedOrDuplicateSourceFixes() {
        assertFalse(GpsFixOrderRules.accept(1000,1000))
        assertFalse(GpsFixOrderRules.accept(999,1000))
        assertFalse(GpsFixOrderRules.accept(0,0))
    }
}
