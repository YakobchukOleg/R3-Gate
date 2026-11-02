package com.r3gate.app

/**
 * Stable event identity for persisted GPS evidence. A replay with the same
 * evidence timestamp, event type and object must have the same ID.
 * This is not an authorization to alter confirmed shifts.
 */
object GpsEventIdentity {
    fun id(kind:String, timestampMs:Long, objectId:String?=null):String {
        require(timestampMs>0L) { "GPS event time must be positive" }
        require(kind.isNotBlank()) { "GPS event kind must not be blank" }
        val place=objectId.orEmpty()
        // Length-prefixed fields avoid ambiguous delimiter-based collisions.
        return "gps-v1:${kind.length}:$kind:$timestampMs:${place.length}:$place"
    }

    fun shouldAppend(existingIds:Collection<String>,candidateId:String):Boolean =
        candidateId.isNotBlank() && candidateId !in existingIds
}
