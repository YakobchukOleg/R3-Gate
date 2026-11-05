package com.r3gate.app.backup

import com.r3gate.app.DriveStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

class DriveBackupTransportTest {
    @get:Rule val directory = TemporaryFolder()
    private val bytes = byteArrayOf(3, 9, 2, 7, 1)
    private val session = "https://www.googleapis.com/upload/drive/v3/files?upload_id=first"
    private val replacement = session.replace("first", "second")

    private fun archive() = directory.newFile("backup.zip").apply { writeBytes(bytes) }

    private fun confirmation() = JSONObject().put("id", "drive-id").put("name", "backup.zip")
        .put("parents", JSONArray().put("parent")).put("size", bytes.size)
        .put("mimeType", "application/zip").put("trashed", false)
        .put("md5Checksum", MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) })

    private class Exchange(
        val status: Int, val headers: Map<String, String> = emptyMap(), val payload: String = "{}"
    ) : HttpURLConnection(URI("https://www.googleapis.com").toURL()) {
        val sent = ByteArrayOutputStream()
        lateinit var endpoint: String
        override fun getResponseCode(): Int {
            if (status < 0) throw IOException("Simulated interrupted response")
            return status
        }
        override fun getHeaderField(name: String): String? = headers[name]
        override fun getOutputStream() = sent
        override fun getInputStream() = payload.byteInputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
    }

    private fun uploader(vararg steps: Exchange): DriveBackupUpload {
        val next = steps.iterator()
        return DriveBackupUpload(DriveStore { endpoint ->
            assertTrue("Unexpected request", next.hasNext())
            next.next().also { it.endpoint = endpoint }
        }, "test-token")
    }

    @Test fun lostResponseResumesUsingServerRangeAndExactRemainingBytes() {
        val file = archive()
        val begin = Exchange(200, mapOf("Location" to session))
        val lost = Exchange(-1)
        var saved: String? = null
        assertThrows(IOException::class.java) {
            uploader(begin, lost).upload("drive-id", "parent", file, saved, { saved = it }) {
                if (lost.sent.size() > 0) assertEquals(session, saved)
            }
        }
        assertEquals(session, saved)
        assertEquals("POST", begin.requestMethod)
        assertEquals("drive-id", JSONObject(begin.sent.toString("UTF-8")).getString("id"))
        assertArrayEquals(bytes, lost.sent.toByteArray())

        val probe = Exchange(308, mapOf("Range" to "bytes=0-1"))
        val tail = Exchange(201, payload = confirmation().toString())
        uploader(probe, tail).upload("drive-id", "parent", file, saved, { fail("Session must be reused") }) {}
        assertEquals("PUT", probe.requestMethod)
        assertEquals(session, probe.endpoint)
        assertEquals("bytes */5", probe.getRequestProperty("Content-Range"))
        assertEquals(0, probe.sent.size())
        assertEquals("bytes 2-4/5", tail.getRequestProperty("Content-Range"))
        assertArrayEquals(bytes.copyOfRange(2, 5), tail.sent.toByteArray())
    }

    @Test fun expiredSessionRecreatesWithOriginalDriveId() {
        val probe = Exchange(404)
        val begin = Exchange(200, mapOf("Location" to replacement))
        val finish = Exchange(200, payload = confirmation().toString())
        val persisted = mutableListOf<String?>()
        uploader(probe, begin, finish).upload("drive-id", "parent", archive(), session, persisted::add) {}
        assertEquals(listOf(null, replacement), persisted)
        assertEquals("drive-id", JSONObject(begin.sent.toString("UTF-8")).getString("id"))
        assertEquals(replacement, finish.endpoint)
        assertArrayEquals(bytes, finish.sent.toByteArray())
    }

    @Test fun completedProbeDoesNotSendTheArchiveAgain() {
        val probe = Exchange(200, payload = confirmation().toString())
        uploader(probe).upload("drive-id", "parent", archive(), session, { fail("Already complete") }) {}
        assertEquals("bytes */5", probe.getRequestProperty("Content-Range"))
        assertEquals(0, probe.sent.size())
    }

    @Test fun conflictReadsExistingMetadataWithoutOverwriting() {
        val conflict = Exchange(409)
        val read = Exchange(200, payload = confirmation().toString())
        uploader(conflict, read).upload("drive-id", "parent", archive(), null, { fail("No new session") }) {}
        assertEquals("GET", read.requestMethod)
        assertTrue(read.endpoint.contains("/files/drive-id?"))
        assertEquals(0, read.sent.size())
    }

    @Test fun conflictWithDifferentChecksumDoesNotReportSuccess() {
        val conflict = Exchange(409)
        val read = Exchange(200, payload = confirmation().put("md5Checksum", "different").toString())
        assertThrows(IOException::class.java) {
            uploader(conflict, read).upload("drive-id", "parent", archive(), null, {}) {}
        }
    }
}
