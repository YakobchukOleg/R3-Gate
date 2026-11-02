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
        objects.any { it.id == objectId && !it.archived }

    /** Keep archived objects addressable for historical views. */
    fun canResolveHistory(objectId: String, objects: Collection<ObjectRef>): Boolean =
        objectId.isNotBlank() && objects.any { it.id == objectId }

    /** Historical records make hard deletion unsafe; archive instead. */
    fun canHardDelete(objectId: String, objects: Collection<ObjectRef>, references: References): Boolean =
        objects.any { it.id == objectId } && references.countFor(objectId) == 0
}
