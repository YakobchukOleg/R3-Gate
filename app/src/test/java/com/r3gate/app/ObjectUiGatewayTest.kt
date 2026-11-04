package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class ObjectUiGatewayTest {
    private val site = ObjectUiGateway.Summary("site", "KOP-01", "Kopytkowo", "24", "Test", false)
    private val archive = site.copy(id = "old", archived = true)
    private val gateway = ObjectUiGateway(listOf(site, archive))

    @Test fun archivedObjectIsStillAvailableToHistory() {
        assertEquals(listOf(site), gateway.listObjects(false))
        assertEquals(listOf(site, archive), gateway.listObjects(true))
        assertEquals(archive, gateway.getObject("old"))
        assertEquals(listOf(site), gateway.selectableObjects())
    }

    @Test fun archiveAndRestorePreserveIdAndDisplayFields() {
        assertEquals(site.copy(archived = true), gateway.setArchived(site.id, true))
        assertEquals(archive.copy(archived = false), gateway.setArchived(archive.id, false))
        assertNull(gateway.setArchived("missing", false))
    }

    @Test fun newLinksCannotUseArchiveButExistingLinksCanStay() {
        assertFalse(gateway.validateAssociation("old", null, true))
        assertTrue(gateway.validateAssociation("old", "old", true))
        assertFalse(gateway.validateAssociation("", null, true))
        assertTrue(gateway.validateAssociation("", null, false))
    }

    @Test fun duplicateIdsCannotBeSelectedAssignedOrMutated() {
        val duplicate = site.copy(archived = true)
        val catalog = ObjectUiGateway(listOf(site, duplicate))
        assertTrue(catalog.selectableObjects().isEmpty())
        assertFalse(catalog.validateAssociation(site.id, null, true))
        assertNull(catalog.getObject(site.id))
        assertNull(catalog.setArchived(site.id, false))
        assertTrue(catalog.validateAssociation(site.id, site.id, true))
    }

    @Test fun draftPreservesStableIdAndAllowsGeneratedTags() {
        assertNull(gateway.validateObjectDraft(site.copy(id = "new", tag = ""), null))
        assertEquals(ObjectUiGateway.DraftError.INVALID_ID, gateway.validateObjectDraft(site, null))
        assertEquals(ObjectUiGateway.DraftError.INVALID_ID, gateway.validateObjectDraft(site.copy(id = "new"), site.id))
        assertEquals(ObjectUiGateway.DraftError.OBJECT_NOT_FOUND, gateway.validateObjectDraft(site.copy(id = "new"), "new"))
    }

    @Test fun draftRejectsMissingAddressOrInvalidCustomPrefix() {
        val draft = site.copy(id = "new", tag = "")
        assertEquals(ObjectUiGateway.DraftError.MISSING_ADDRESS, gateway.validateObjectDraft(draft.copy(address = ""), null))
        assertEquals(ObjectUiGateway.DraftError.INVALID_TAG_PREFIX, gateway.validateObjectDraft(draft, null, "???"))
    }
}
