package com.r3gate.app

/**
 * Pure domain checks for links to work objects. Object IDs are permanent identifiers:
 * archiving or renaming an object must not detach its historical records.
 */
object ObjectIntegrityRules {
    data class ObjectRef(val id: String, val archived: Boolean = false)
    data class RecordRef(val objectId: String)
    data class References(
        val shifts: List<RecordRef> = emptyList(),
        val stages: List<RecordRef> = emptyList(),
        val materials: List<RecordRef> = emptyList(),
        val photos: List<RecordRef> = emptyList(),
        val notes: List<RecordRef> = emptyList()
    ) {
        fun countFor(id: String): Int =
            (shifts + stages + materials + photos + notes).count { it.objectId == id }
    }

    fun validateNewId(id: String, existing: Collection<ObjectRef>): Boolean =
        id.isNotBlank() && existing.none { it.id == id }

    fun canAssign(objectId: String, objects: Collection<ObjectRef>): Boolean =
        objectId.isNotBlank() && objects.singleOrNull { it.id == objectId }?.archived == false

    /**
     * Legacy records can retain their original association even when their object
     * has since been archived or is missing from an old backup. Changing the
     * association, however, always requires an existing active object.
     */
    fun canSaveAssignment(
        newObjectId: String,
        previousObjectId: String?,
        objects: Collection<ObjectRef>
    ): Boolean =
        newObjectId.isBlank() || (previousObjectId != null && previousObjectId == newObjectId) ||
            canAssign(newObjectId, objects)

    /**
     * Car stock is independent of an object. Materials recorded at an object
     * require a valid destination, except for preserving an unchanged legacy link.
     */
    fun canSaveMaterialAssignment(
        storage: String,
        newObjectId: String,
        previousObjectId: String?,
        objects: Collection<ObjectRef>
    ): Boolean =
        if (storage == "car") newObjectId.isBlank()
        else newObjectId.isNotBlank() &&
            canSaveAssignment(newObjectId, previousObjectId, objects)

    /** A photo entry must contain media or an existing Drive link and have a safe object reference. */
    fun canSavePhotoEntry(
        photoCount: Int,
        driveLink: String,
        newObjectId: String,
        previousObjectId: String?,
        objects: Collection<ObjectRef>
    ): Boolean =
        (photoCount > 0 || driveLink.isNotBlank()) &&
            newObjectId.isNotBlank() &&
            canSaveAssignment(newObjectId, previousObjectId, objects)

    /** Keep archived objects addressable for historical views. */
    fun canResolveHistory(objectId: String, objects: Collection<ObjectRef>): Boolean =
        objectId.isNotBlank() && objects.any { it.id == objectId }

    /** Historical records make hard deletion unsafe; archive instead. */
    fun canHardDelete(objectId: String, objects: Collection<ObjectRef>, references: References): Boolean =
        objectId.isNotBlank() && objects.count { it.id == objectId } == 1 && references.countFor(objectId) == 0
}
