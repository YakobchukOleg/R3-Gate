package com.r3gate.app.backup

import com.r3gate.app.DriveHttpException
import com.r3gate.app.DriveStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.time.Instant

class DriveBackupDownloadTest {
    @get:Rule val directory = TemporaryFolder()
    private val bytes = byteArrayOf(9, 2, 7, 4)
    private val checksum = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
    private val copy get() = DriveBackupCatalog.Copy("drive-id", "backup.zip", Instant.EPOCH, bytes.size.toLong(), checksum)

    private class Response(val bytes: ByteArray, private val status: Int = 200) :
        HttpURLConnection(URI("https://www.googleapis.com").toURL()) {
        var disconnected = false
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun disconnect() { disconnected = true }
        override fun connect() = Unit
        override fun usingProxy() = false
    }

    @Test fun verifiesCompleteDownloadAndUsesSelectedFile() {
        val response = Response(bytes)
        val target = directory.newFile()
        val download = DriveBackupDownload(DriveStore { endpoint ->
            assertEquals("https://www.googleapis.com/drive/v3/files/drive-id?alt=media", endpoint)
            response
        }, "test-token")
        download.write(copy, target)
        assertArrayEquals(bytes, target.readBytes())
        assertEquals("GET", response.requestMethod)
        assertFalse(response.instanceFollowRedirects)
        assertEquals("Bearer test-token", response.getRequestProperty("Authorization"))
        assertTrue(response.disconnected)
    }

    @Test fun truncatedDownloadRemovesPartialFile() {
        val target = directory.newFile()
        val response = Response(bytes.copyOf(2))
        assertThrows(IOException::class.java) {
            DriveBackupDownload(DriveStore { response }, "token").write(copy, target)
        }
        assertFalse(target.exists())
        assertTrue(response.disconnected)
    }

    @Test fun oversizedDownloadRemovesPartialFile() {
        val target = directory.newFile()
        val response = Response(bytes + byteArrayOf(1))
        assertThrows(IOException::class.java) {
            DriveBackupDownload(DriveStore { response }, "token").write(copy, target)
        }
        assertFalse(target.exists())
    }

    @Test fun matchingLengthWithWrongChecksumIsRejected() {
        val target = directory.newFile()
        val response = Response(byteArrayOf(1, 2, 3, 4))
        assertThrows(IOException::class.java) {
            DriveBackupDownload(DriveStore { response }, "token").write(copy, target)
        }
        assertFalse(target.exists())
    }

    @Test fun cancelAfterReceivingBytesDiscardsDownloadedCopy() {
        val target = directory.newFile()
        val response = Response(bytes)
        var checks = 0
        assertThrows(InterruptedException::class.java) {
            DriveBackupDownload(DriveStore { response }, "token").write(copy, target) {
                if (++checks >= 3) throw InterruptedException()
            }
        }
        assertFalse(target.exists())
        assertTrue(response.disconnected)
    }

    @Test fun accountChangeAfterDownloadPreventsSuccess() {
        val target = directory.newFile()
        val response = Response(bytes)
        var checks = 0
        assertThrows(IllegalStateException::class.java) {
            DriveBackupDownload(DriveStore { response }, "token").write(copy, target) {
                if (++checks == 4) error("Account changed")
            }
        }
        assertFalse(target.exists())
    }

    @Test fun revokedAccessReturnsHttpStatusAndDeletesTemporaryFile() {
        val target = directory.newFile()
        val response = Response(byteArrayOf(), 401)
        val failure = assertThrows(DriveHttpException::class.java) {
            DriveBackupDownload(DriveStore { response }, "token").write(copy, target)
        }
        assertEquals(401, failure.status)
        assertFalse(target.exists())
        assertTrue(response.disconnected)
    }

    @Test fun rejectsUnsafeRemoteIdBeforeRequest() {
        val target = directory.newFile()
        assertThrows(IllegalArgumentException::class.java) {
            DriveBackupDownload(DriveStore { error("Unexpected network request") }, "token")
                .write(copy.copy(id = "../unrelated"), target)
        }
    }
}
