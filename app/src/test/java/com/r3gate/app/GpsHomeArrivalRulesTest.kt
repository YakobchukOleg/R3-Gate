package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsHomeArrivalRulesTest {
    private val rules=GpsHomeArrivalRules
    private fun ready(
        enabled:Boolean=true,active:Boolean=true,shift:Boolean=true,
        confirmed:String?="object-1",nearest:String?="object-1",
        fix:Long=100_000L,evidence:Long=100_000L,now:Long=100_000L
    )=rules.canChooseObject(enabled,active,shift,confirmed,nearest,fix,evidence,now)

    @Test fun confirmedRecentArrivalEnablesPicker() { assertTrue(ready()) }
    @Test fun trackingDisabledDisablesPicker() { assertFalse(ready(enabled=false)) }
    @Test fun serviceStoppedDisablesPicker() { assertFalse(ready(active=false)) }
    @Test fun noShiftDisablesPicker() { assertFalse(ready(shift=false)) }
    @Test fun noConfirmedObjectDisablesPicker() { assertFalse(ready(confirmed=null)) }
    @Test fun blankConfirmedObjectDisablesPicker() { assertFalse(ready(confirmed="")) }
    @Test fun unrecognizedCurrentLocationDisablesPicker() { assertFalse(ready(nearest=null)) }
    @Test fun locationAtDifferentObjectDisablesPicker() { assertFalse(ready(nearest="object-2")) }
    @Test fun oldFixDisablesPicker() { assertFalse(ready(fix=100_000L,now=100_000L+rules.MAX_FIX_AGE_MS+1)) }
    @Test fun oldArrivalEvidenceDisablesPicker() { assertFalse(ready(evidence=100_000L,fix=200_000L,now=100_000L+rules.MAX_FIX_AGE_MS+1)) }
    @Test fun fixAtFreshnessBoundaryIsAccepted() { assertTrue(ready(fix=100_000L,evidence=100_000L,now=100_000L+rules.MAX_FIX_AGE_MS)) }
    @Test fun futureFixIsNotAccepted() { assertFalse(ready(fix=200_000L,now=100_000L)) }
    @Test fun futureArrivalEvidenceIsNotAccepted() { assertFalse(ready(evidence=200_000L,now=100_000L)) }
    @Test fun missingFixTimeIsNotAccepted() { assertFalse(ready(fix=0L)) }
    @Test fun missingArrivalTimeIsNotAccepted() { assertFalse(ready(evidence=0L)) }
    @Test fun invalidClockIsNotAccepted() { assertFalse(ready(now=0L)) }
    @Test fun consistentNearbyLocationOnlyRequiresPositiveTimes() {
        assertTrue(ready(fix=1L,evidence=1L,now=1L))
    }
}
