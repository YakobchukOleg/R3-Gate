package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

/** All 16 permission/provider combinations: no approximate-only false GPS-off warning. */
class GpsProviderAvailabilityMatrixTest {
    private fun actual(gps:Boolean,network:Boolean,fine:Boolean,coarse:Boolean) =
        GpsProviderRules.availabilityProblem(gps,network,fine,coarse)
    @Test fun matrix00() {
        assertEquals(GpsProviderRules.AvailabilityProblem.PERMISSION,
            actual(false,false,false,false))
    }
    @Test fun matrix01() {
        assertEquals(GpsProviderRules.AvailabilityProblem.LOCATION_OFF,
            actual(false,false,false,true))
    }
    @Test fun matrix02() {
        assertEquals(GpsProviderRules.AvailabilityProblem.LOCATION_OFF,
            actual(false,false,true,false))
    }
    @Test fun matrix03() {
        assertEquals(GpsProviderRules.AvailabilityProblem.LOCATION_OFF,
            actual(false,false,true,true))
    }
    @Test fun matrix04() {
        assertEquals(GpsProviderRules.AvailabilityProblem.PERMISSION,
            actual(false,true,false,false))
    }
    @Test fun matrix05() {
        assertEquals(null,
            actual(false,true,false,true))
    }
    @Test fun matrix06() {
        assertEquals(null,
            actual(false,true,true,false))
    }
    @Test fun matrix07() {
        assertEquals(null,
            actual(false,true,true,true))
    }
    @Test fun matrix08() {
        assertEquals(GpsProviderRules.AvailabilityProblem.PERMISSION,
            actual(true,false,false,false))
    }
    @Test fun matrix09() {
        assertEquals(GpsProviderRules.AvailabilityProblem.INCOMPATIBLE_PROVIDER,
            actual(true,false,false,true))
    }
    @Test fun matrix10() {
        assertEquals(null,
            actual(true,false,true,false))
    }
    @Test fun matrix11() {
        assertEquals(null,
            actual(true,false,true,true))
    }
    @Test fun matrix12() {
        assertEquals(GpsProviderRules.AvailabilityProblem.PERMISSION,
            actual(true,true,false,false))
    }
    @Test fun matrix13() {
        assertEquals(null,
            actual(true,true,false,true))
    }
    @Test fun matrix14() {
        assertEquals(null,
            actual(true,true,true,false))
    }
    @Test fun matrix15() {
        assertEquals(null,
            actual(true,true,true,true))
    }
    @Test fun fineGpsRestorationResolvesCoarseIncompatibility() {
        assertEquals(GpsProviderRules.AvailabilityProblem.INCOMPATIBLE_PROVIDER,
            actual(true,false,false,true))
        assertNull(actual(true,false,true,true))
    }
    @Test fun networkProviderRestorationAllowsCoarseTracking() {
        assertEquals(GpsProviderRules.AvailabilityProblem.INCOMPATIBLE_PROVIDER,
            actual(true,false,false,true))
        assertNull(actual(true,true,false,true))
    }
    @Test fun allLocationProvidersOffRegardlessOfFinePermission() {
        assertEquals(GpsProviderRules.AvailabilityProblem.LOCATION_OFF,
            actual(false,false,true,true))
    }
    @Test fun deniedPermissionTakesPrecedenceOverDisabledProviders() {
        assertEquals(GpsProviderRules.AvailabilityProblem.PERMISSION,
            actual(false,false,false,false))
    }
}
