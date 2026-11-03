package com.r3gate.app.backup

import org.json.JSONArray
import org.json.JSONObject

/** Rewrites only photo URI values in an isolated copy; stable IDs and metadata are preserved. */
object PhotoRestoreJournal {
    fun remap(originalJson: String, uriChanges: Map<String, String>): String {
        val root = JSONObject(originalJson)
        val seen = HashSet<String>()
        fun transform(value: Any, depth: Int): Any {
            require(depth < 32) { "Journal nesting too deep" }
            return when (value) {
                is JSONObject -> JSONObject().also { result ->
                    for (key in value.keys()) {
                        val child = value.get(key)
                        result.put(key, if (key == "photos") {
                            require(child is JSONArray) { "Invalid photos field" }
                            JSONArray().also { mapped ->
                                for (index in 0 until child.length()) {
                                    val old = child.get(index)
                                    require(old is String && old.isNotBlank()) { "Invalid photo URI" }
                                    val replacement = requireNotNull(uriChanges[old]) {
                                        "Missing replacement photo URI"
                                    }
                                    require(replacement.startsWith("content://")) { "Replacement is not a content URI" }
                                    seen.add(old)
                                    mapped.put(replacement)
                                }
                            }
                        } else if (child is JSONObject || child is JSONArray) {
                            transform(child, depth + 1)
                        } else child)
                    }
                }
                is JSONArray -> JSONArray().also { result ->
                    for (index in 0 until value.length()) {
                        val child = value.get(index)
                        result.put(if (child is JSONObject || child is JSONArray) transform(child, depth + 1) else child)
                    }
                }
                else -> value
            }
        }
        val remapped = transform(root, 0) as JSONObject
        require(seen == uriChanges.keys) { "URI map does not match journal references" }
        return remapped.toString()
    }
}
