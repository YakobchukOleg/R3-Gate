package com.r3gate.app.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DriveBackupCatalogTest {
    private val name = "WorkMeter-SOLO-2026-11-04_20-10-00_00000000-0000-0000-0000-000000000001.wmb.zip"
    private fun file() = JSONObject().put("id", "drive-id").put("name", name)
        .put("modifiedTime", "2026-11-04T20:10:00Z").put("size", "42")
        .put("md5Checksum", "ABCDEF0123456789ABCDEF0123456789")
        .put("trashed", false).put("mimeType", "application/zip")

    @Test fun parsesSupportedZip() {
        assertEquals(DriveBackupCatalog.Copy("drive-id", name, Instant.parse("2026-11-04T20:10:00Z"),
            42, "abcdef0123456789abcdef0123456789"), DriveBackupCatalog.parse(file()))
    }
    @Test fun ignoresLegacyAndUnrelatedFiles() {
        assertNull(DriveBackupCatalog.parse(file().put("name", "WorkMeter-backup-04-11-2026.json")))
        assertNull(DriveBackupCatalog.parse(file().put("name", "unrelated.zip")))
    }
    @Test fun rejectsDeletedAndNonZipFiles() {
        assertNull(DriveBackupCatalog.parse(file().put("trashed", true)))
        assertNull(DriveBackupCatalog.parse(file().put("mimeType", "application/json")))
    }
    @Test fun rejectsMetadataWithoutSafeDownloadBounds() {
        assertNull(DriveBackupCatalog.parse(file().put("size", 0)))
        assertNull(DriveBackupCatalog.parse(file().put("size", DriveBackupCatalog.MAX_DOWNLOAD_BYTES + 1)))
        assertNull(DriveBackupCatalog.parse(file().put("id", "../other")))
        assertNull(DriveBackupCatalog.parse(file().put("md5Checksum", "")))
    }
    @Test fun encodesPageTokenAsData() {
        val url = DriveBackupCatalog.listUrl("a+b&x=1")
        assertTrue(url.endsWith("&pageToken=a%2Bb%26x%3D1"))
        assertTrue(url.contains("nextPageToken"))
    }
}
