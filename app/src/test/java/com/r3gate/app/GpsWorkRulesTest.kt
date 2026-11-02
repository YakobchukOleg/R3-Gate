package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsWorkRulesTest {
    private val base=GpsWorkRules.Point(54.092,18.776,8.0,100000)
    @Test fun geozoneMatchesKnownPlaceAndRejectsInaccurateFix() {
        val places=listOf(GpsWorkRules.Place("base",GpsWorkRules.Kind.BASE,54.092,18.776,100.0))
        assertEquals("base",GpsWorkRules.nearest(base,places)?.place?.id)
        assertNull(GpsWorkRules.nearest(base.copy(accuracyM=250.0),places))
    }
    @Test fun movingAndStoppingAreNotConfusedWithGpsNoise() {
        val move=GpsWorkRules.Point(54.097,18.776,8.0,160000)
        assertTrue(GpsWorkRules.traveling(base,move))
        assertFalse(GpsWorkRules.traveling(base,base.copy(timestampMs=160000,latitude=54.09201)))
        assertFalse(GpsWorkRules.traveling(base,move.copy(accuracyM=400.0)))
    }
    @Test fun confirmsArrivalAfterDwellAndOptionalStopThreshold() {
        assertFalse(GpsWorkRules.stable(1000,30000,60000))
        assertTrue(GpsWorkRules.stable(1000,61000,60000))
        assertTrue(GpsWorkRules.longStop(0,300000,GpsWorkRules.Settings(stopMinutes=5)))
        assertFalse(GpsWorkRules.longStop(0,300000,GpsWorkRules.Settings(notifyLongStops=false)))
    }
    @Test fun commutePayRulesSeparateElapsedAndPayableHours() {
        val activity=GpsWorkRules.Activity.OBJECT
        val segments=listOf(
            GpsWorkRules.Segment(0,3600000,GpsWorkRules.Activity.TRAVEL,"outbound"),
            GpsWorkRules.Segment(3600000,7200000,GpsWorkRules.Activity.OBJECT),
            GpsWorkRules.Segment(7200000,9000000,GpsWorkRules.Activity.SHOPPING),
            GpsWorkRules.Segment(9000000,10800000,GpsWorkRules.Activity.TRAVEL,"return"))
        assertEquals(10800000L to 5400000L,GpsWorkRules.totals(segments,GpsWorkRules.PayRules()))
        assertEquals(10800000L to 10800000L,GpsWorkRules.totals(segments,GpsWorkRules.PayRules(outboundCommute=true,returnCommute=true)))
    }
    @Test fun temporaryIdIsStableAndDepartureMustHaveDwell() {
        assertEquals("temporary-36000000",GpsWorkRules.temporaryObjectId(36000000))
        assertFalse(GpsWorkRules.stable(10000,20000,30000))
        assertTrue(GpsWorkRules.stable(10000,40000,30000))
    }
}
