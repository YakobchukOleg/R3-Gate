package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class GpsEventContractTest {
    @Test fun confidenceDistinguishesEvidenceFromActionableProposals() {
        assertEquals(GpsEventContract.Confidence.LOW,GpsEventContract.confidence("GPS_GAP"))
        assertEquals(GpsEventContract.Confidence.MEDIUM,GpsEventContract.confidence("TRIP_START"))
        assertEquals(GpsEventContract.Confidence.MEDIUM,GpsEventContract.confidence("OBJECT_VISIT"))
        assertEquals(GpsEventContract.Confidence.HIGH,GpsEventContract.confidence("ARRIVAL"))
    }

    @Test fun metadataIsProvisionalEvenForHighConfidence() {
        val data=GpsEventContract.metadata("DEPARTURE",3_000L,1_000L)
        assertEquals("PROVISIONAL",data["confirmation"])
        assertEquals("gps-v1",data["source"])
        assertEquals("HIGH",data["confidence"])
        assertEquals(3_000L,data["timestampMs"])
        assertEquals(1_000L,data["evidenceStartMs"])
        assertEquals(3_000L,data["evidenceEndMs"])
    }

    @Test fun tripStartAndStopKeepSameTripId() {
        val id=GpsEventIdentity.id("TRIP_START",3_000L)
        val start=GpsEventContract.metadata("TRIP_START",3_000L,1_000L,id)
        val stop=GpsEventContract.metadata("TRIP_STOP",5_000L,4_000L,id)
        assertEquals(id,start["tripId"])
        assertEquals(start["tripId"],stop["tripId"])
    }

    @Test fun gapDoesNotInventAnEvidenceInterval() {
        val data=GpsEventContract.metadata("GPS_GAP",3_000L,4_000L)
        assertFalse(data.containsKey("evidenceStartMs"))
        assertFalse(data.containsKey("evidenceEndMs"))
        assertFalse(data.containsKey("tripId"))
    }

    @Test fun unknownKindCannotAcquireProposalMetadata() {
        assertTrue(GpsEventContract.metadata("UNKNOWN",3_000L,1_000L).isEmpty())
    }
}
