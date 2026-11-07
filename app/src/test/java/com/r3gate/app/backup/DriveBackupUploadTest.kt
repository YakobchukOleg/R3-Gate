package com.r3gate.app.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveBackupUploadTest {
    private fun metadata() = JSONObject().put("id", "drive-id").put("name", "backup.zip")
        .put("parents", JSONArray().put("folder-id")).put("size", "42")
        .put("md5Checksum", "DEADBEEF").put("trashed", false).put("mimeType", "application/zip")

    private fun matches(file: JSONObject) =
        DriveBackupUpload.matches(file, "drive-id", "folder-id", "backup.zip", 42, "deadbeef")

    @Test fun acceptsExactArchiveMetadata() = assertTrue(matches(metadata()))
    @Test fun refusesWrongRemoteId() = assertFalse(matches(metadata().put("id", "different")))
    @Test fun refusesWrongFolder() = assertFalse(matches(metadata().put("parents", JSONArray().put("different"))))
    @Test fun refusesTruncatedArchive() = assertFalse(matches(metadata().put("size", "41")))
    @Test fun refusesDifferentBytes() = assertFalse(matches(metadata().put("md5Checksum", "ffffffff")))
    @Test fun refusesMissingChecksum() {
        val file = metadata()
        file.remove("md5Checksum")
        assertFalse(matches(file))
    }
    @Test fun refusesDeletedArchive() = assertFalse(matches(metadata().put("trashed", true)))
    @Test fun refusesWrongFileType() = assertFalse(matches(metadata().put("mimeType", "application/json")))
    @Test fun companionJsonRequiresItsRequestedMimeAndExactChecksum() {
        val file=metadata().put("mimeType","application/json")
        assertTrue(DriveBackupUpload.matches(file,"drive-id","folder-id","backup.zip",42,"deadbeef","application/json"))
        assertFalse(DriveBackupUpload.matches(file,"drive-id","folder-id","backup.zip",42,"other","application/json"))
    }
    @Test fun diagnosticsCannotBeMistakenForObjectCatalog() {
        val file=metadata().put("mimeType","application/x-ndjson")
        assertFalse(DriveBackupUpload.matches(file,"drive-id","folder-id","backup.zip",42,"deadbeef","application/json"))
    }
}
