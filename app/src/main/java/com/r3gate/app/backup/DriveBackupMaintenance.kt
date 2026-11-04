package com.r3gate.app.backup

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

internal object DriveBackupMaintenance {
    private val graceMillis = TimeUnit.DAYS.toMillis(1)

    private val active = mutableSetOf<UUID>()

    fun <T> whileActive(id: UUID, run: () -> T): T {
        synchronized(this) { check(active.add(id)) }
        return try {
            run()
        } finally {
            synchronized(this) { active.remove(id) }
        }
    }

    /** Only private, UUID-named queue directories are eligible. Absent jobs may have been pruned. */
    @Synchronized
    fun removeStaleDirectories(
        root: File, activeId: UUID, unfinished: Set<UUID>, now: Long = System.currentTimeMillis()
    ): Set<UUID> = root.listFiles().orEmpty().mapNotNull { directory ->
        val id = runCatching { UUID.fromString(directory.name) }.getOrNull()
        val modified = directory.lastModified()
        if (id == null || id.toString() != directory.name || id == activeId || id in active || id in unfinished ||
            !directory.isDirectory || java.nio.file.Files.isSymbolicLink(directory.toPath()) ||
            modified <= 0 || modified > now - graceMillis
        ) return@mapNotNull null
        id.takeIf { directory.deleteRecursively() }
    }.toSet()
}
