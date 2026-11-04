package com.r3gate.app

/** UI-facing rules over the saved object catalog. Persistence remains with the Core host. */
class ObjectUiGateway(objects: List<Summary>) {
    data class Summary(
        val id: String,
        val tag: String,
        val city: String,
        val address: String,
        val name: String,
        val archived: Boolean
    )

    enum class DraftError { INVALID_ID, OBJECT_NOT_FOUND, MISSING_CITY, MISSING_ADDRESS, INVALID_TAG_PREFIX }

    private val catalog = objects.toList()
    private val byId = catalog.groupBy { it.id }
    private val references = catalog.map { ObjectIntegrityRules.ObjectRef(it.id, it.archived) }

    fun listObjects(includeArchived: Boolean): List<Summary> =
        catalog.filter { includeArchived || !it.archived }

    fun getObject(id: String): Summary? =
        if (id.isBlank()) null else byId[id]?.singleOrNull()

    fun selectableObjects(): List<Summary> =
        catalog.filter { !it.archived && it.id.isNotBlank() && byId[it.id]?.size == 1 }

    fun validateObjectDraft(draft: Summary, previousId: String?, customPrefix: String = ""): DraftError? =
        when {
            draft.id.isBlank() -> DraftError.INVALID_ID
            previousId == null && !ObjectIntegrityRules.validateNewId(draft.id, references) -> DraftError.INVALID_ID
            previousId != null && draft.id != previousId -> DraftError.INVALID_ID
            previousId != null && getObject(previousId) == null -> DraftError.OBJECT_NOT_FOUND
            draft.city.isBlank() -> DraftError.MISSING_CITY
            draft.address.isBlank() -> DraftError.MISSING_ADDRESS
            draft.tag.isBlank() && customPrefix.isNotBlank() &&
                DomainRules.customPrefix(customPrefix).isBlank() -> DraftError.INVALID_TAG_PREFIX
            else -> null
        }

    /** A change candidate for the host to persist by ID, retaining any extra stored fields. */
    fun setArchived(id: String, archived: Boolean): Summary? =
        getObject(id)?.copy(archived = archived)

    fun validateAssociation(objectId: String, previousObjectId: String?, required: Boolean): Boolean =
        (!required || objectId.isNotBlank()) &&
            ObjectIntegrityRules.canSaveAssignment(objectId, previousObjectId, references)
}
