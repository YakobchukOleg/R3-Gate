package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsLocationQualityTest {
    @Test fun freshAccurateFixIsAccepted() {
        assertTrue(GpsLocationQuality.acceptable(200_000,190_000,15.0,54.35,18.65))
    }
    @Test fun cachedOldLocationCannotCreateFalseTravel() {
        assertFalse(GpsLocationQuality.acceptable(400_000,200_000,10.0,54.35,18.65))
    }
    @Test fun futureAndUnknownTimestampsRejected() {
        assertFalse(GpsLocationQuality.acceptable(200_000,220_001,10.0,54.35,18.65))
        assertFalse(GpsLocationQuality.acceptable(200_000,0,10.0,54.35,18.65))
    }
    @Test fun invalidPrecisionAndGeographyRejected() {
        assertFalse(GpsLocationQuality.acceptable(200_000,200_000,101.0,54.35,18.65))
        assertFalse(GpsLocationQuality.acceptable(200_000,200_000,Double.NaN,54.35,18.65))
        assertFalse(GpsLocationQuality.acceptable(200_000,200_000,10.0,91.0,18.65))
    }
    @Test fun ageAndClockSkewBoundaryInclusive() {
        assertTrue(GpsLocationQuality.acceptable(200_000,80_000,10.0,0.0,0.0))
        assertTrue(GpsLocationQuality.acceptable(200_000,210_000,10.0,0.0,0.0))
    }
}
