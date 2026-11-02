package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class ObjectIntegrityRulesTest {
    private val active = ObjectIntegrityRules.ObjectRef("obj-1")
    private val archived = ObjectIntegrityRules.ObjectRef("obj-2", archived = true)
    private val objects = listOf(active, archived)

    @Test fun newObjectIdsMustBeNonEmptyAndUnique() {
        assertFalse(ObjectIntegrityRules.validateNewId("", objects))
        assertFalse(ObjectIntegrityRules.validateNewId("obj-1", objects))
        assertFalse(ObjectIntegrityRules.validateNewId("obj-2", objects))
        assertTrue(ObjectIntegrityRules.validateNewId("obj-3", objects))
    }

    @Test fun archivedObjectCannotReceiveNewAssignments() {
        assertTrue(ObjectIntegrityRules.canAssign("obj-1", objects))
        assertFalse(ObjectIntegrityRules.canAssign("obj-2", objects))
        assertFalse(ObjectIntegrityRules.canAssign("unknown", objects))
    }

    @Test fun archivedObjectRemainsAvailableForHistoricalRecords() {
        assertTrue(ObjectIntegrityRules.canResolveHistory("obj-2", objects))
        assertFalse(ObjectIntegrityRules.canResolveHistory("", objects))
        assertFalse(ObjectIntegrityRules.canResolveHistory("missing", objects))
    }

    @Test fun referencesPreventDestructiveDeletionAcrossAllRecordTypes() {
        val linked = ObjectIntegrityRules.RecordRef("obj-1")
        val examples = listOf(
            ObjectIntegrityRules.References(shifts = listOf(linked)),
            ObjectIntegrityRules.References(stages = listOf(linked)),
            ObjectIntegrityRules.References(materials = listOf(linked)),
            ObjectIntegrityRules.References(photos = listOf(linked)),
            ObjectIntegrityRules.References(notes = listOf(linked))
        )
        examples.forEach { assertFalse(ObjectIntegrityRules.canHardDelete("obj-1", objects, it)) }
        assertTrue(ObjectIntegrityRules.canHardDelete("obj-1", objects, ObjectIntegrityRules.References()))
        assertFalse(ObjectIntegrityRules.canHardDelete("missing", objects, ObjectIntegrityRules.References()))
    }

    @Test fun referencesToOtherObjectsDoNotBlockDeletion() {
        val references = ObjectIntegrityRules.References(
            shifts = listOf(ObjectIntegrityRules.RecordRef("obj-2")),
            materials = listOf(ObjectIntegrityRules.RecordRef("obj-2"))
        )
        assertEquals(0, references.countFor("obj-1"))
        assertTrue(ObjectIntegrityRules.canHardDelete("obj-1", objects, references))
    }
}
