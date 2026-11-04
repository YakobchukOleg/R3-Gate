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
import java.util.UUID

/** Create-only upload. The caller persists the Drive ID and archive before sending bytes. */
internal class DriveBackupUpload(private val drive: DriveStore, private val token: String) {
    fun allocateId(): String =
        drive.json("GET", "$API/files/generateIds?count=1&space=drive", token)
            .getJSONArray("ids").getString(0)

    fun upload(id: String, parent: String, file: File, checkActive: () -> Unit) {
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
            send(file, metadata, checkActive)
        } catch (failure: DriveHttpException) {
            if (failure.status != 409) throw failure
            drive.json("GET", "$API/files/$id?fields=$FIELDS", token)
        }
        if (!matches(uploaded, id, parent, file.name, file.length(), checksum)) {
            throw IOException("Drive did not confirm the uploaded archive")
        }
    }

    private fun send(file: File, metadata: JSONObject, checkActive: () -> Unit): JSONObject {
        val boundary = "workmeter-${UUID.randomUUID()}"
        val header = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
            "$metadata\r\n--$boundary\r\nContent-Type: application/zip\r\n\r\n").toByteArray()
        val trailer = "\r\n--$boundary--\r\n".toByteArray()
        val connection = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=$FIELDS")
            .openConnection() as HttpURLConnection
        try {
            checkActive()
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            connection.setFixedLengthStreamingMode(header.size + file.length() + trailer.size)
            connection.outputStream.use { output ->
                output.write(header)
                file.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        checkActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
                checkActive()
                output.write(trailer)
            }
            val status = connection.responseCode
            if (status !in 200..299) throw DriveHttpException(status)
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val FIELDS = "id,name,parents,size,md5Checksum,trashed,mimeType"

        internal fun matches(file: JSONObject, id: String, parent: String, name: String, size: Long, md5: String): Boolean =
            file.optString("id") == id && file.optString("name") == name &&
                file.optJSONArray("parents")?.optString(0) == parent &&
                file.optLong("size", -1) == size && file.optString("md5Checksum").equals(md5, true) &&
                !file.optBoolean("trashed") && file.optString("mimeType") == "application/zip"
    }
}
