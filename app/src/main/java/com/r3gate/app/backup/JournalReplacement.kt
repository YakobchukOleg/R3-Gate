package com.r3gate.app.backup

/** A detected concurrent edit must never be overwritten by a failed restore's rollback. */
internal class JournalRestoreConflict : IllegalStateException("Journal changed during restore")

internal object JournalReplacement {
    fun commit(
        original: String,
        replacement: String,
        read: () -> String,
        write: (String) -> Boolean,
        recovery: () -> String
    ) {
        if (read() != original) throw JournalRestoreConflict()
        var failure: Exception? = null
        val written = try { write(replacement) } catch (cause: Exception) { failure = cause; false }
        val observed = read()
        if (written && observed == replacement) return
        if (observed != original && observed != replacement) throw JournalRestoreConflict()
        if (observed == replacement) {
            val previous = recovery()
            check(previous == original) { "Recovery checkpoint changed" }
            // Recovery verification does I/O; check again before touching current data.
            if (read() != replacement) throw JournalRestoreConflict()
            check(write(previous) && read() == previous) {
                "Rollback failed; verified recovery archive retained"
            }
        }
        throw IllegalStateException("Restore failed; recovery archive retained", failure)
    }
}
