package com.r3gate.app.backup

import org.json.JSONArray
import org.json.JSONObject

/** Extracts nested photo references without rewriting original journal records. */
object PhotoArchiveJournal {
    private const val MAX_NODES = 100_000
    fun references(json: String): List<String> {
        val root = JSONObject(json)
        val photos = ArrayList<String>()
        var nodes = 0
        fun visit(value: Any, depth: Int, fromPhotoList: Boolean = false) {
            require(depth <= 24 && ++nodes <= MAX_NODES) { "Journal nesting limit exceeded" }
            when (value) {
                is JSONObject -> {
                    for (key in value.keys()) {
                        val child = value.get(key)
                        if (key == "photos") {
                            require(child is JSONArray) { "Invalid photos array" }
                            visit(child, depth + 1, true)
                        } else if (child is JSONObject || child is JSONArray) {
                            visit(child, depth + 1)
                        }
                    }
                }
                is JSONArray -> for (i in 0 until value.length()) {
                    val child = value.get(i)
                    if (fromPhotoList) {
                        require(child is String && child.isNotBlank()) { "Invalid photo reference" }
                        photos.add(child)
                        require(photos.size <= PhotoArchiveIndex.MAX_PHOTOS * 4) { "Too many references" }
                    } else if (child is JSONObject || child is JSONArray) {
                        visit(child, depth + 1)
                    }
                }
            }
        }
        visit(root, 0)
        return PhotoArchiveIndex.build(photos).map { it.uri }
    }
}
