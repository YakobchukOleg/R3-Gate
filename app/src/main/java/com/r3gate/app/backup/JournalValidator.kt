package com.r3gate.app.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only validation of a journal before any restore is allowed.
 * Object references are reported, never rewritten or silently dropped.
 */
object JournalValidator {
    data class Report(
        val objectCount: Int,
        val archivedCount: Int,
        val referenceCount: Int,
        val warnings: List<String>,
        val errors: List<String>
    ) {
        val canRestore: Boolean get() = errors.isEmpty()
    }

    fun inspect(json: String): Report {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val root = try { JSONObject(json) } catch (_: Exception) {
            return Report(0, 0, 0, emptyList(), listOf("Invalid JSON object"))
        }
        // Existing model fields must never silently become empty collections.
        for (key in listOf("objects", "shifts", "stages", "materials", "photos", "links",
            "notes", "logs", "payments", "expenses")) {
            if (root.has(key) && root.optJSONArray(key) == null) {
                errors.add("$key is not an array")
            }
        }
        if (root.has("active") && root.optJSONObject("active") == null) {
            errors.add("active is not an object")
        }
        val objects = root.optJSONArray("objects") ?: JSONArray()
        val ids = mutableSetOf<String>()
        var archived = 0
        for (i in 0 until objects.length()) {
            val obj = objects.optJSONObject(i)
            if (obj == null) { errors.add("objects[$i] is not an object"); continue }
            if (obj.has("id") && obj.opt("id") !is String) {
                errors.add("objects[$i].id is not a string")
            }
            if (obj.has("archived") && obj.opt("archived") !is Boolean) {
                errors.add("objects[$i].archived is not a boolean")
            }
            val id = (obj.opt("id") as? String)?.trim().orEmpty()
            if (id.isEmpty()) errors.add("objects[$i] has no ID")
            else if (!ids.add(id)) errors.add("Duplicate object ID: $id")
            if (obj.optBoolean("archived", false)) archived++
        }
        var references = 0
        fun check(value: JSONObject, location: String) {
            if (value.has("objectId") && value.opt("objectId") !is String) {
                errors.add("$location.objectId is not a string")
                return
            }
            val id = (value.opt("objectId") as? String)?.trim().orEmpty()
            if (id.isNotEmpty()) {
                references++
                if (id !in ids) warnings.add("$location references unknown objectId: $id")
            }
        }
        for (key in listOf("shifts", "stages", "materials", "photos", "links", "notes", "logs", "payments", "expenses")) {
            val array = root.optJSONArray(key) ?: continue
            for (i in 0 until array.length()) {
                val value = array.optJSONObject(i)
                if (value == null) { errors.add("$key[$i] is not an object"); continue }
                check(value, "$key[$i]")
                if (key == "shifts") {
                    if (value.has("stages") && value.optJSONArray("stages") == null) {
                        errors.add("shifts[$i].stages is not an array")
                        continue
                    }
                    val stages = value.optJSONArray("stages") ?: continue
                    for (j in 0 until stages.length()) {
                        val stage = stages.optJSONObject(j)
                        if (stage == null) errors.add("shifts[$i].stages[$j] is not an object")
                        else check(stage, "shifts[$i].stages[$j]")
                    }
                }
            }
        }
        return Report(ids.size, archived, references, warnings, errors)
    }
}
