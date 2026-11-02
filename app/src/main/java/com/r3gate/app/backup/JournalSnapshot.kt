package com.r3gate.app.backup

import java.io.ByteArrayInputStream
import java.io.OutputStream

/** Writes a caller-supplied, unchanged JSON snapshot into a versioned offline archive.
 * All object IDs, historical references and archived objects remain untouched.
 */
object JournalSnapshot {
    const val ENTRY = "journal/data.json"

    fun write(json: String, target: OutputStream): WorkMeterArchive.Inspection {
        require(json.trimStart().startsWith("{") && json.trimEnd().endsWith("}")) { "Expected JSON object snapshot" }
        val bytes = json.toByteArray(Charsets.UTF_8)
        return WorkMeterArchive.write(target, listOf(
            WorkMeterArchive.Source(ENTRY) { ByteArrayInputStream(bytes) }
        ))
    }
}
