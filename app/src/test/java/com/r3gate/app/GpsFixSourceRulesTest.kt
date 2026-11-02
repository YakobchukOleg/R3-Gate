package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsFixSourceRulesTest {
    @Test fun acceptsCurrentProviderFix() {
        assertTrue(GpsFixSourceRules.accept("gps","gps",true))
        assertTrue(GpsFixSourceRules.accept("network","network",true))
    }
    @Test fun rejectsStaleProviderCallbackAfterHandover() {
        assertFalse(GpsFixSourceRules.accept("network","gps",true))
        assertFalse(GpsFixSourceRules.accept("gps","network",true))
    }
    @Test fun rejectsFixWhenTrackingStoppedOrNoProvider() {
        assertFalse(GpsFixSourceRules.accept(null,"gps",true))
        assertFalse(GpsFixSourceRules.accept("gps",null,true))
        assertFalse(GpsFixSourceRules.accept("gps","gps",false))
    }
}
