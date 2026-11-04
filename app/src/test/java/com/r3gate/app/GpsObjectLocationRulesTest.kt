package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsObjectLocationRulesTest {
    private val rules=GpsObjectLocationRules
    private fun capture(id:String="obj-1",lat:Double=54.352,lon:Double=18.646,
                        accuracy:Double=10.0,time:Long=1_000_000L) =
        GpsObjectLocationRules.Capture(id,lat,lon,accuracy,time)

    @Test fun validLocationCanBeAssignedToMatchingObject() {
        assertTrue(rules.belongsTo(capture(),"obj-1",1_001_000L))
    }
    @Test fun coordinatesCannotBeAssignedToDifferentObject() {
        assertFalse(rules.belongsTo(capture(),"obj-2",1_001_000L))
    }
    @Test fun blankObjectIdIsRejected() {
        assertFalse(rules.valid(capture(id=""),1_001_000L))
    }
    @Test fun whitespaceOnlyObjectIdIsRejected() {
        assertFalse(rules.valid(capture(id=" "),1_001_000L))
    }
    @Test fun boundaryLatitudeIsAllowed() {
        assertTrue(rules.valid(capture(lat=90.0),1_001_000L))
    }
    @Test fun latitudeOutsideEarthIsRejected() {
        assertFalse(rules.valid(capture(lat=90.1),1_001_000L))
    }
    @Test fun boundaryLongitudeIsAllowed() {
        assertTrue(rules.valid(capture(lon=-180.0),1_001_000L))
    }
    @Test fun longitudeOutsideEarthIsRejected() {
        assertFalse(rules.valid(capture(lon=-180.1),1_001_000L))
    }
    @Test fun poorLocationAccuracyIsRejected() {
        assertFalse(rules.valid(capture(accuracy=150.0),1_001_000L))
    }
    @Test fun invalidNaNCoordinatesAreRejected() {
        assertFalse(rules.valid(capture(lat=Double.NaN),1_001_000L))
    }
    @Test fun stalePositionCannotBeSaved() {
        assertFalse(rules.valid(capture(),1_000_000L+GpsLocationQuality.MAX_AGE_MS+1))
    }
    @Test fun boundaryAgeRemainsAllowed() {
        assertTrue(rules.valid(capture(),1_000_000L+GpsLocationQuality.MAX_AGE_MS))
    }
    @Test fun futureDatedPositionBeyondClockToleranceIsRejected() {
        assertFalse(rules.valid(capture(time=1_100_001L),1_000_000L))
    }
    @Test fun locationIsIndependentOfTagAndUsesObjectId() {
        assertTrue(rules.belongsTo(capture(id="uuid-7"),"uuid-7",1_001_000L))
    }
    @Test fun geofenceUsesOneHundredMeterRadius() {
        assertEquals(100.0,GpsObjectLocationRules.RADIUS_M,0.0)
    }
}
