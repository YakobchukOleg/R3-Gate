package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsEventIdentityTest {
    @Test fun replayKeepsIdStable() {
        assertEquals(GpsEventIdentity.id("TRIP_START",12345L),
            GpsEventIdentity.id("TRIP_START",12345L))
    }
    @Test fun eventTypesCannotShareId() {
        assertNotEquals(GpsEventIdentity.id("TRIP_START",12345L),
            GpsEventIdentity.id("TRIP_STOP",12345L))
    }
    @Test fun objectIdChangesIdentity() {
        assertNotEquals(GpsEventIdentity.id("ARRIVAL",12345L,"site-a"),
            GpsEventIdentity.id("ARRIVAL",12345L,"site-b"))
    }
    @Test fun timestampChangesIdentity() {
        assertNotEquals(GpsEventIdentity.id("TRIP_STOP",12345L),
            GpsEventIdentity.id("TRIP_STOP",12346L))
    }
    @Test fun delimiterCharactersCannotCollide() {
        assertNotEquals(GpsEventIdentity.id("ARRIVAL:1",12345L,"A"),
            GpsEventIdentity.id("ARRIVAL",12345L,"1:A"))
    }
    @Test fun duplicateIsNotAppended() {
        val id=GpsEventIdentity.id("GPS_GAP",12345L)
        assertFalse(GpsEventIdentity.shouldAppend(listOf(id),id))
    }
    @Test fun newEventCanBeAppended() {
        val old=GpsEventIdentity.id("GPS_GAP",12345L)
        val next=GpsEventIdentity.id("GPS_GAP",12346L)
        assertTrue(GpsEventIdentity.shouldAppend(listOf(old),next))
    }
    @Test fun emptyIdCannotBeAppended() {
        assertFalse(GpsEventIdentity.shouldAppend(emptyList(),""))
    }
    @Test(expected=IllegalArgumentException::class)
    fun invalidTimestampRejected() {
        GpsEventIdentity.id("ARRIVAL",0L)
    }
    @Test(expected=IllegalArgumentException::class)
    fun missingKindRejected() {
        GpsEventIdentity.id(" ",12345L)
    }

    @Test fun legacyTripReplayIsDetected() {
        assertTrue(GpsEventIdentity.sameEvent(null,"TRIP_START",100L,null,
            "TRIP_START",100L))
    }
    @Test fun legacyGeofenceReplayIsDetected() {
        assertTrue(GpsEventIdentity.sameEvent("","ARRIVAL",100L,"object-1",
            "ARRIVAL",100L,"object-1"))
    }
    @Test fun legacyDifferentObjectIsNotDuplicate() {
        assertFalse(GpsEventIdentity.sameEvent(null,"ARRIVAL",100L,"object-1",
            "ARRIVAL",100L,"object-2"))
    }
    @Test fun legacyDifferentTimeIsNotDuplicate() {
        assertFalse(GpsEventIdentity.sameEvent(null,"GPS_GAP",100L,null,
            "GPS_GAP",101L))
    }
    @Test fun legacyDifferentKindIsNotDuplicate() {
        assertFalse(GpsEventIdentity.sameEvent(null,"TRIP_START",100L,null,
            "TRIP_STOP",100L))
    }
    @Test fun newIdTakesPrecedenceOverLegacyFields() {
        val id=GpsEventIdentity.id("TRIP_STOP",100L)
        assertTrue(GpsEventIdentity.sameEvent(id,"incorrect",999L,null,
            "TRIP_STOP",100L))
    }
    @Test fun newIdMismatchCannotBecomeLegacyMatch() {
        val id=GpsEventIdentity.id("TRIP_START",100L)
        assertFalse(GpsEventIdentity.sameEvent(id,"TRIP_STOP",100L,null,
            "TRIP_STOP",100L))
    }
}
