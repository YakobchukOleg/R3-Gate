package com.r3gate.app.backup

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Two-phase restore coordinator. The Activity owns the Session and must display its
 * preview before calling commit(confirmed=true); this class never prompts the user.
 *
 * Staged photos are already in private, FileProvider-accessible storage. They are
 * retained on successful commit and deleted on cancellation. No existing photos are
 * overwritten. MainActivity/Drive settings are not touched.
 */
object PhotoRestoreCoordinator {
    private val lock = Any()
    private const val PREFERENCES = "r3_gate"

    class Session internal constructor(
        val preview: RestorePlan.Preview,
        val photoCount: Int,
        private val originalJournal: String,
        private val prepared: PhotoRestoreAndroid.Prepared,
        private val snapshots: Map<String, String>
    ) {
        val stagingDirectory: File get() = prepared.staged.directory
        val summary: RestoreSummary = RestoreSummary.from(preview, photoCount)
        private var finished = false

        /** Explicitly cancel a pending restore, deleting its private staged photos. */
        fun discard() = synchronized(lock) {
            check(!finished) { "Restore session already finished" }
            finished = true
            prepared.discard()
        }

        /**
         * Applies the verified replacement only after explicit confirmation. If the
         * underlying journal was edited after preview, it is left unchanged. On a
         * failed commit, staged files are removed only when rollback is confirmed.
         */
        fun commit(context: Context, confirmed: Boolean): JournalRestoreStore.Outcome =
            synchronized(lock) {
                check(!finished) { "Restore session already finished" }
                require(confirmed) { "Explicit restore confirmation required" }
                val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                val current = prefs.getString("data", "{}")
                    ?: throw IllegalStateException("Current journal missing")

                // Re-check the old journal before any operation that could write.
                RestorePlan.confirmedReplacement(preview, current, true)
                verifyFiles(prepared, snapshots)

                val rewritten = rewrittenJournal(context, originalJournal, prepared)
                require(rewritten == prepared.journalJson) { "Restore photo URIs changed" }
                val modified = preview.copy(
                    decision = RestorePlan.Decision.REPLACE_REQUIRES_CONFIRMATION,
                    incomingSha256 = digest(rewritten),
                    journalJson = rewritten
                )
                try {
                    val result = JournalRestoreStore.restore(context, modified, true)
                    if (result == JournalRestoreStore.Outcome.UNCHANGED || prepared.staged.files.isEmpty()) prepared.discard()
                    finished = true
                    result
                } catch (failure: Exception) {
                    // Do not delete photos if an unsuccessful rollback left references.
                    if (prefs.getString("data", null) == current) {
                        prepared.discard()
                        finished = true
                    }
                    throw failure
                }
            }

        private fun verifyFiles(
            prepared: PhotoRestoreAndroid.Prepared, expected: Map<String, String>
        ) {
            val directory = prepared.staged.directory.canonicalFile
            require(directory.isDirectory) { "Staged directory no longer exists" }
            require(prepared.staged.files.keys == expected.keys) { "Staged photo set changed" }
            for ((uri, file) in prepared.staged.files) {
                require(file.isFile && !java.nio.file.Files.isSymbolicLink(file.toPath())) {
                    "Staged photo missing"
                }
                require(file.canonicalFile.parentFile == directory) { "Photo escaped staging directory" }
                require(digestFile(file) == expected[uri]) { "Staged photo bytes changed" }
            }
        }
    }

    fun prepare(
        context: Context, openArchive: () -> InputStream
    ): Session = synchronized(lock) {
        val current = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString("data", "{}") ?: throw IllegalStateException("Current journal missing")
        val plan = RestorePlan.prepare(openArchive, current)
        val prepared = PhotoRestoreAndroid.prepare(context, openArchive)
        try {
            val rewritten = rewrittenJournal(context, plan.journalJson, prepared)
            require(prepared.journalJson == rewritten) { "Archive changed between verification passes" }
            val snapshots = prepared.staged.files.mapValues { (_, file) -> digestFile(file) }
            val decision = if (rewritten == current) RestorePlan.Decision.NO_CHANGES
                else RestorePlan.Decision.REPLACE_REQUIRES_CONFIRMATION
            Session(plan.copy(decision = decision), prepared.remappedCount, plan.journalJson, prepared, snapshots)
        } catch (failure: Exception) {
            prepared.discard()
            throw failure
        }
    }

    private fun rewrittenJournal(
        context: Context, original: String, prepared: PhotoRestoreAndroid.Prepared
    ): String {
        val mapping = prepared.staged.files.mapValues { (_, file) ->
            FileProvider.getUriForFile(context, "com.r3gate.app.photos", file).toString()
        }
        return PhotoRestoreJournal.remap(original, mapping)
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun digestFile(file: File): String {
        val sha = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { source ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                if (count > 0) sha.update(buffer, 0, count)
            }
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }
}
