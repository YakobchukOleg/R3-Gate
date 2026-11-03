package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsSamplingSafetyTest {
    @Test fun quietWithFreshAccurateLocationUsesLongInterval() {
        assertEquals(90_000L,GpsSamplingSafety.interval(90_000,200_000,190_000,12.0,false))
    }
    @Test fun staleOrMissingGpsKeepsFrequentUpdates() {
        assertEquals(30_000L,GpsSamplingSafety.interval(90_000,300_000,100_000,12.0,false))
        assertEquals(30_000L,GpsSamplingSafety.interval(90_000,300_000,0,12.0,false))
    }
    @Test fun uncertainGpsDoesNotReduceSampling() {
        assertEquals(30_000L,GpsSamplingSafety.interval(90_000,300_000,290_000,85.0,false))
        assertEquals(30_000L,GpsSamplingSafety.interval(90_000,300_000,290_000,Double.NaN,false))
    }
    @Test fun knownTravelAlwaysKeepsFrequentUpdates() {
        assertEquals(30_000L,GpsSamplingSafety.interval(90_000,300_000,290_000,10.0,true))
    }
    @Test fun futureLocationTimestampIsNotTrusted() {
        assertEquals(30_000L,GpsSamplingSafety.interval(90_000,300_000,310_000,10.0,false))
    }
}
