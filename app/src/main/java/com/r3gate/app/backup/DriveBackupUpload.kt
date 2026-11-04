package com.r3gate.app.backup

import com.r3gate.app.DriveHttpException
import com.r3gate.app.DriveStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.io.RandomAccessFile
import java.net.URI

/** Create-only upload. The caller persists the Drive ID and archive before sending bytes. */
internal class DriveBackupUpload(private val drive: DriveStore, private val token: String) {
    fun allocateId(): String =
        drive.json("GET", "$API/files/generateIds?count=1&space=drive", token)
            .getJSONArray("ids").getString(0)

    fun upload(
        id: String, parent: String, file: File, session: String?,
        saveSession: (String?) -> Unit, checkActive: () -> Unit
    ) {
        require(id.matches(Regex("[A-Za-z0-9_-]+")))
        val metadata = JSONObject().put("id", id).put("name", file.name)
            .put("mimeType", "application/zip").put("parents", JSONArray().put(parent))
        val checksum = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("MD5") // Compare against Drive's transport checksum.
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                checkActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        val uploaded = try {
            send(file, metadata, session, saveSession, checkActive)
        } catch (failure: DriveHttpException) {
            if (failure.status != 409) throw failure
            drive.json("GET", "$API/files/$id?fields=$FIELDS", token)
        }
        if (!matches(uploaded, id, parent, file.name, file.length(), checksum)) {
            throw IOException("Drive did not confirm the uploaded archive")
        }
    }

    private fun send(
        file: File, metadata: JSONObject, savedSession: String?,
        saveSession: (String?) -> Unit, checkActive: () -> Unit
    ): JSONObject {
        val size = file.length()
        require(size > 0)
        var session = savedSession?.takeIf(::validSession)
        var offset = 0L
        if (session != null) {
            checkActive()
            when (val status = exchange(session, "bytes */$size", 0) {}) {
                is Reply.Complete -> return status.metadata
                is Reply.Partial -> offset = nextOffset(status.range, size)
                Reply.Expired -> {
                    session = null
                    saveSession(null)
                }
            }
        }
        val activeSession = session ?: begin(metadata, size, checkActive).also(saveSession)
        return RandomAccessFile(file, "r").use { input ->
            sendChunks(size, offset) { start, count ->
                checkActive()
                input.seek(start)
                val range = "bytes $start-${start + count - 1}/$size"
                exchange(activeSession, range, count) { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var remaining = count
                    while (remaining > 0) {
                        checkActive()
                        val length = minOf(remaining, buffer.size.toLong()).toInt()
                        input.readFully(buffer, 0, length)
                        output.write(buffer, 0, length)
                        remaining -= length
                    }
                }.also { if (it == Reply.Expired) saveSession(null) }
            }
        }
    }

    private fun begin(metadata: JSONObject, size: Long, checkActive: () -> Unit): String {
        checkActive()
        val connection = connection("$UPLOAD?uploadType=resumable&fields=$FIELDS", "POST")
        try {
            val body = metadata.toString().toByteArray(Charsets.UTF_8)
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("X-Upload-Content-Type", "application/zip")
            connection.setRequestProperty("X-Upload-Content-Length", size.toString())
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode !in 200..299) throw DriveHttpException.from(connection)
            return connection.getHeaderField("Location")?.takeIf(::validSession)
                ?: throw IOException("Drive returned an invalid upload session")
        } finally {
            connection.disconnect()
        }
    }

    private fun exchange(
        session: String, range: String, length: Long, write: (java.io.OutputStream) -> Unit
    ): Reply {
        val connection = connection(session, "PUT")
        try {
            connection.setRequestProperty("Content-Range", range)
            connection.setRequestProperty("Content-Type", "application/zip")
            connection.setFixedLengthStreamingMode(length)
            connection.outputStream.use(write)
            return when (connection.responseCode) {
                200, 201 -> Reply.Complete(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
                308 -> Reply.Partial(connection.getHeaderField("Range"))
                404, 410 -> Reply.Expired
                else -> throw DriveHttpException.from(connection)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun connection(url: String, method: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
        }

    internal sealed interface Reply {
        data class Complete(val metadata: JSONObject) : Reply
        data class Partial(val range: String?) : Reply
        data object Expired : Reply
    }

    companion object {
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        internal const val CHUNK_BYTES = 8L * 1024 * 1024
        private val ACK = Regex("bytes=0-([0-9]+)")

        internal fun validSession(value: String): Boolean {
            val uri = runCatching { URI(value) }.getOrNull() ?: return false
            return uri.scheme == "https" && uri.host == "www.googleapis.com" &&
                uri.port in listOf(-1, 443) && uri.userInfo == null && uri.fragment == null &&
                uri.path == "/upload/drive/v3/files" && !uri.rawQuery.isNullOrBlank()
        }

        internal fun nextOffset(range: String?, size: Long): Long {
            if (range == null) return 0
            val last = ACK.matchEntire(range)?.groupValues?.get(1)?.toLongOrNull()
            if (last == null || last !in 0 until size - 1) throw IOException("Invalid Drive upload range")
            return last + 1
        }

        internal fun sendChunks(size: Long, start: Long, send: (Long, Long) -> Reply): JSONObject {
            var offset = start
            while (offset in 0 until size) {
                val length = minOf(CHUNK_BYTES, size - offset)
                when (val response = send(offset, length)) {
                    is Reply.Complete -> return response.metadata
                    is Reply.Partial -> {
                        val next = nextOffset(response.range, size)
                        if (next <= offset || next > offset + length) throw IOException("Invalid Drive upload progress")
                        offset = next
                    }
                    Reply.Expired -> throw IOException("Drive upload session expired")
                }
            }
            throw IOException("Drive did not confirm upload completion")
        }

        private const val FIELDS = "id,name,parents,size,md5Checksum,trashed,mimeType"

        internal fun matches(file: JSONObject, id: String, parent: String, name: String, size: Long, md5: String): Boolean =
            file.optString("id") == id && file.optString("name") == name &&
                file.optJSONArray("parents")?.optString(0) == parent &&
                file.optLong("size", -1) == size && file.optString("md5Checksum").equals(md5, true) &&
                !file.optBoolean("trashed") && file.optString("mimeType") == "application/zip"
    }
}
