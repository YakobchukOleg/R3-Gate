package com.r3gate.app.backup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
class BackupCompanionsTest {
    @get:Rule val temp=TemporaryFolder()
    private val extras=mapOf(BackupCompanions.OBJECTS to "{\"objects\":[]}".toByteArray(),BackupCompanions.DIAGNOSTICS to "{\"kind\":\"GPS_FIX\"}\n".toByteArray())
    @Test fun companionsAreManifestProtectedAndJournalIsUnchanged() {
        val out=ByteArrayOutputStream();PhotoArchivePackage.write("{}",emptyList(),{ error("no photos") },out,extras)
        val open={ ByteArrayInputStream(out.toByteArray()) }
        assertEquals("{}",ArchivePreflight.verify(open).journalJson);assertArrayEquals(extras[BackupCompanions.DIAGNOSTICS],BackupCompanions.read(open,BackupCompanions.DIAGNOSTICS))
        assertTrue(PhotoArchivePackage.verifyComplete(open,emptyList()).complete)
    }
    @Test fun olderArchivesRemainReadable() {
        val out=ByteArrayOutputStream();PhotoArchivePackage.write("{}",emptyList(),{ error("no photos") },out)
        assertNull(BackupCompanions.read({ByteArrayInputStream(out.toByteArray())},BackupCompanions.DIAGNOSTICS))
    }
    @Test fun standaloneFilesMatchVerifiedArchiveBytes() {
        val archive=LocalBackupStore.create(temp.root,"{}",{ error("no photos") },1,attachments=extras)
        assertArrayEquals(extras[BackupCompanions.OBJECTS],BackupCompanions.file(archive,BackupCompanions.OBJECTS).readBytes())
    }
    @Test fun retentionPrunesOnlyCompanionsOfRemovedArchives() {
        val first=LocalBackupStore.create(temp.root,"{}",{error("no photos")},1,attachments=extras)
        val next=LocalBackupStore.create(temp.root,"{}",{error("no photos")},1,attachments=extras)
        assertFalse(BackupCompanions.file(first,BackupCompanions.DIAGNOSTICS).exists());assertTrue(BackupCompanions.file(next,BackupCompanions.DIAGNOSTICS).exists())
    }
}
