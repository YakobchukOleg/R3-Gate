package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class CoreGpsProposalStoreTest {
    private class MemoryStorage(var value: String? = null, var failWrite: Boolean = false) :
        CoreGpsProposalStore.Storage {
        var writes = 0
        override fun read(key: String): String? {
            assertEquals(CoreGpsProposalStore.KEY, key)
            return value
        }
        override fun write(key: String, value: String): Boolean {
            assertEquals(CoreGpsProposalStore.KEY, key)
            writes++
            if (failWrite) return false
            this.value = value
            return true
        }
    }

    private fun event(
        id: String = "arrival-1",
        kind: CoreGpsProposalInbox.Kind = CoreGpsProposalInbox.Kind.ARRIVAL
    ) = CoreGpsProposalInbox.Event(
        id, kind, 1_800_000_000_000L, "object-1", "trip-A",
        CoreGpsProposalInbox.Confidence.MEDIUM,
        evidenceStartMs = 1_799_999_999_000L,
        evidenceEndMs = 1_800_000_000_000L
    )
    private val objects = setOf("object-1")

    @Test fun emptyStoreIsValidAndHasNoProposals() {
        assertEquals(emptyList<CoreGpsProposalInbox.Event>(),
            (CoreGpsProposalStore.decode(null) as CoreGpsProposalStore.ReadResult.Success).events)
    }

    @Test fun versionedRoundTripPreservesAllFieldsAndUserDecision() {
        val original = event("id:\t\n/ł")
            .copy(decision = CoreGpsProposalInbox.Decision.USER_REJECTED, objectId = null, tripId = null)
        val serialized = CoreGpsProposalStore.encode(listOf(original))
        val restored = CoreGpsProposalStore.decode(serialized) as CoreGpsProposalStore.ReadResult.Success
        assertEquals(listOf(original), restored.events)
    }

    @Test fun importsAreDurableAndReplayIsIdempotent() {
        val storage = MemoryStorage()
        val first = CoreGpsProposalStore.import(storage, listOf(event()), objects)
        assertTrue(first.changed)
        val bytes = storage.value
        assertEquals(listOf(event()), (CoreGpsProposalStore.read(storage) as CoreGpsProposalStore.ReadResult.Success).events)
        val replay = CoreGpsProposalStore.import(storage, listOf(event()), objects)
        assertFalse(replay.changed)
        assertEquals(1, storage.writes)
        assertEquals(bytes, storage.value)
        assertEquals(1, replay.imported!!.ignoredDuplicates)
    }

    @Test fun decisionSurvivesReloadAndRepeatedGpsDelivery() {
        val storage = MemoryStorage()
        CoreGpsProposalStore.import(storage, listOf(event()), objects)
        assertTrue(CoreGpsProposalStore.decide(storage, "arrival-1",
            CoreGpsProposalInbox.Decision.USER_CONFIRMED).changed)
        val afterRestart = MemoryStorage(storage.value)
        val replay = CoreGpsProposalStore.import(afterRestart, listOf(event()), objects)
        assertFalse(replay.changed)
        assertEquals(CoreGpsProposalInbox.Decision.USER_CONFIRMED,
            (CoreGpsProposalStore.read(afterRestart) as CoreGpsProposalStore.ReadResult.Success)
                .events.single().decision)
    }

    @Test fun corruptedStorageIsNeverOverwritten() {
        val storage = MemoryStorage("unknown\nmalformed")
        assertTrue(CoreGpsProposalStore.read(storage) is CoreGpsProposalStore.ReadResult.Corrupt)
        val result = CoreGpsProposalStore.import(storage, listOf(event()), objects)
        assertFalse(result.changed)
        assertEquals(0, storage.writes)
        assertEquals("unknown\nmalformed", storage.value)
    }

    @Test fun duplicateIdsAndUnknownEnumValuesCannotLoad() {
        val encoded = CoreGpsProposalStore.encode(listOf(event()))
        val row = encoded.substringAfter('\n')
        assertTrue(CoreGpsProposalStore.decode(encoded + "\n" + row) is CoreGpsProposalStore.ReadResult.Corrupt)
        val badEnum = encoded.replace("\tARRIVAL\t", "\tUNKNOWN\t")
        assertTrue(CoreGpsProposalStore.decode(badEnum) is CoreGpsProposalStore.ReadResult.Corrupt)
    }

    @Test fun failedWriteDoesNotReportSuccessOrLoseOriginal() {
        val storage = MemoryStorage(failWrite = true)
        val result = CoreGpsProposalStore.import(storage, listOf(event()), objects)
        assertFalse(result.changed)
        assertTrue(result.state is CoreGpsProposalStore.ReadResult.Corrupt)
        assertNull(storage.value)
    }

    @Test fun rejectedProposalCannotBeSilentlyConfirmedAfterRestart() {
        val storage = MemoryStorage()
        CoreGpsProposalStore.import(storage, listOf(event()), objects)
        CoreGpsProposalStore.decide(storage, "arrival-1", CoreGpsProposalInbox.Decision.USER_REJECTED)
        assertFalse(CoreGpsProposalStore.decide(storage, "arrival-1",
            CoreGpsProposalInbox.Decision.USER_CONFIRMED).changed)
        assertEquals(CoreGpsProposalInbox.Decision.USER_REJECTED,
            (CoreGpsProposalStore.read(storage) as CoreGpsProposalStore.ReadResult.Success)
                .events.single().decision)
    }

    @Test fun invalidIncomingEvidenceDoesNotCauseWrite() {
        val storage = MemoryStorage()
        val result = CoreGpsProposalStore.import(storage, listOf(event().copy(timestampMs = 0L)), objects)
        assertFalse(result.changed)
        assertEquals(1, result.imported!!.rejectedInvalid)
        assertEquals(0, storage.writes)
    }
}
