package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant

class LocalBackupStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val journal = """{"objects":[{"id":"old","archived":true}],"links":[{"objectId":"old","photos":["content://photo"]}],"future":{"value":7}}"""

    private fun create(retain: Int = 2, instant: Instant = Instant.parse("2026-11-04T12:00:00Z")) =
        LocalBackupStore.create(temporary.root, journal, { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }, retain, instant)

    @Test fun publishesVerifiedArchivePreservingJournalAndPhotos() {
        val file = create()
        assertTrue(file.name.startsWith("WorkMeter-SOLO-2026-11-04_"))
        assertEquals(journal, ArchivePreflight.verify { file.inputStream() }.journalJson)
        assertEquals(1, PhotoArchivePackage.verifyComplete({ file.inputStream() }, listOf("content://photo")).photoCount)
    }

    @Test fun missingPhotoKeepsPreviousGoodArchiveAndLeavesNoPartialFile() {
        val good = create()
        val original = good.readBytes()
        assertThrows(FileNotFoundException::class.java) {
            LocalBackupStore.create(temporary.root, journal, { throw FileNotFoundException() }, 1)
        }
        assertArrayEquals(original, good.readBytes())
        assertEquals(listOf(good), temporary.root.listFiles()?.toList())
    }

    @Test fun retentionIgnoresUnrelatedFilesAndKeepsNewCopyEvenWhenClockMovesBack() {
        val other = File(temporary.root, "keep.txt").apply { writeText("other") }
        val previous = create()
        val current = create(1, Instant.parse("2025-11-04T12:00:00Z"))
        assertEquals(listOf(current), LocalBackupStore.list(temporary.root))
        assertFalse(previous.exists())
        assertTrue(other.exists())
    }

    @Test fun interruptedAttemptCannotPublishOrPrune() {
        val good = create()
        assertThrows(InterruptedException::class.java) {
            LocalBackupStore.create(temporary.root, journal, { ByteArrayInputStream(byteArrayOf(1)) }, 1,
                beforePublish = { throw InterruptedException() })
        }
        assertEquals(listOf(good), temporary.root.listFiles()?.toList())
    }

    @Test fun staleStagingIsRemovedWithoutTouchingOtherTemporaryFiles() {
        val stale = File(temporary.root, ".workmeter-local-previous.tmp").apply { writeText("partial") }
        val other = File(temporary.root, "other.tmp").apply { writeText("other") }
        create()
        assertFalse(stale.exists())
        assertTrue(other.exists())
    }
}
