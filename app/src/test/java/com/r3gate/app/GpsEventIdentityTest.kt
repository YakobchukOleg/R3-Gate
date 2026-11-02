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
}
