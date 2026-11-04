package com.r3gate.app.backup

import com.r3gate.app.backup.DriveBackupUpload.Reply
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DriveResumableUploadTest {
    @Test fun startsAtFirstByteWhenServerHasNoRange() {
        assertEquals(0L, DriveBackupUpload.nextOffset(null, 1024))
    }

    @Test fun resumesAfterLastAcknowledgedByte() {
        assertEquals(43L, DriveBackupUpload.nextOffset("bytes=0-42", 1024))
    }

    @Test fun rejectsInvalidOrOutOfBoundsAcknowledgments() {
        for (range in listOf("bytes=2-42", "bytes=0-1023", "bytes=0-999999999999999999999", "invalid")) {
            assertThrows(IOException::class.java) { DriveBackupUpload.nextOffset(range, 1024) }
        }
    }

    @Test fun usesConfirmedOffsetRatherThanAssumingWholeChunkWasReceived() {
        val chunk = DriveBackupUpload.CHUNK_BYTES
        val calls = mutableListOf<Pair<Long, Long>>()
        val metadata = JSONObject().put("id", "drive-id")
        val result = DriveBackupUpload.sendChunks(chunk + 10, 100) { offset, length ->
            calls += offset to length
            if (calls.size == 1) Reply.Partial("bytes=0-${chunk - 1}") else Reply.Complete(metadata)
        }
        assertSame(metadata, result)
        assertEquals(listOf(100L to chunk - 90L, chunk to 10L), calls)
    }

    @Test fun stopsWhenServerMakesNoProgressOrReportsUnsentBytes() {
        for (range in listOf(null, "bytes=0-${DriveBackupUpload.CHUNK_BYTES}")) {
            assertThrows(IOException::class.java) {
                DriveBackupUpload.sendChunks(DriveBackupUpload.CHUNK_BYTES + 100, 0) { _, _ -> Reply.Partial(range) }
            }
        }
    }

    @Test fun expiredSessionRequiresNewAttempt() {
        assertThrows(IOException::class.java) {
            DriveBackupUpload.sendChunks(1024, 500) { _, _ -> Reply.Expired }
        }
    }

    @Test fun acceptsOnlyTrustedSessionEndpoint() {
        val path = "/upload/drive/v3/files?upload_id=opaque"
        assertTrue(DriveBackupUpload.validSession("https://www.googleapis.com$path"))
        for (url in listOf("https://evil.test$path", "http://www.googleapis.com$path",
            "https://www.googleapis.com.evil.test$path", "https://www.googleapis.com:444$path",
            "https://user@www.googleapis.com$path", "https://www.googleapis.com/drive/v3/files")) {
            assertFalse(DriveBackupUpload.validSession(url))
        }
    }
}
