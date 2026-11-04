package com.r3gate.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class DriveBackupMaintenanceTest {
    @get:Rule val folder = TemporaryFolder()
    private val now = TimeUnit.DAYS.toMillis(4)
    private val old = TimeUnit.DAYS.toMillis(2)

    private fun stage(id: UUID, modified: Long): File = folder.newFolder(id.toString()).apply {
        resolve("copy.zip").writeText("archive")
        check(setLastModified(modified))
    }

    @Test fun removesOnlyOldFinishedOrPrunedJobs() {
        val active = UUID.randomUUID()
        val queued = UUID.randomUUID()
        val stale = UUID.randomUUID()
        val current = stage(active, old)
        val waiting = stage(queued, old)
        val abandoned = stage(stale, old)
        val recent = stage(UUID.randomUUID(), now)
        val unrelated = folder.newFolder("keep")
        check(unrelated.setLastModified(old))

        assertEquals(setOf(stale), DriveBackupMaintenance.removeStaleDirectories(folder.root, active, setOf(queued), now))
        assertFalse(abandoned.exists())
        assertTrue(listOf(current, waiting, recent, unrelated).all(File::isDirectory))
    }

    @Test fun cancellationDoesNotDeleteBytesWhileWorkerStillExits() {
        val stopped = UUID.randomUUID()
        val caller = UUID.randomUUID()
        val directory = stage(stopped, old)
        DriveBackupMaintenance.whileActive(stopped) {
            assertTrue(DriveBackupMaintenance.removeStaleDirectories(folder.root, caller, emptySet(), now).isEmpty())
            assertTrue(directory.exists())
        }
        assertEquals(setOf(stopped), DriveBackupMaintenance.removeStaleDirectories(folder.root, caller, emptySet(), now))
        assertFalse(directory.exists())
    }

    @Test fun keepsDirectoriesWithFutureTimestamps() {
        val directory = stage(UUID.randomUUID(), now + old)
        assertTrue(DriveBackupMaintenance.removeStaleDirectories(folder.root, UUID.randomUUID(), emptySet(), now).isEmpty())
        assertTrue(directory.exists())
    }
}
