package com.r3gate.app.backup

import com.r3gate.app.DriveStore
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant

internal class DriveBackupCatalog(private val drive: DriveStore, private val token: String) {
    fun list(): List<Copy> {
        val copies = linkedMapOf<String, Copy>()
        val seen = mutableSetOf<String>()
        var page: String? = null
        do {
            val response = drive.json("GET", listUrl(page), token)
            val files = response.optJSONArray("files")
            if (files != null) for (index in 0 until files.length()) {
                parse(files.getJSONObject(index))?.let { copies.putIfAbsent(it.id, it) }
            }
            page = response.optString("nextPageToken").takeIf(String::isNotBlank)
            if (page != null && !seen.add(page)) throw IOException("Drive returned a repeated page token")
        } while (page != null)
        return copies.values.sortedByDescending(Copy::modifiedAt)
    }

    fun requireCurrent(copy: Copy) {
        require(copy.id.matches(ID))
        val current = parse(drive.json("GET", "$API/${copy.id}?fields=$FIELDS", token))
        if (current != copy) throw IOException("The selected Drive backup has changed")
    }

    data class Copy(val id: String, val name: String, val modifiedAt: Instant, val size: Long, val md5: String)

    companion object {
        internal const val API = "https://www.googleapis.com/drive/v3/files"
        private const val FIELDS = "id,name,modifiedTime,size,md5Checksum,trashed,mimeType"
        private val ID = Regex("[A-Za-z0-9_-]+")
        private val MD5 = Regex("[a-fA-F0-9]{32}")
        internal const val MAX_DOWNLOAD_BYTES = 9L * 1024 * 1024 * 1024

        internal fun listUrl(page: String?): String {
            val query = "trashed = false and mimeType = 'application/zip' and name contains 'WorkMeter-SOLO-'"
            val url = "$API?spaces=drive&pageSize=100&fields=nextPageToken,files($FIELDS)&q=${encode(query)}"
            return if (page == null) url else "$url&pageToken=${encode(page)}"
        }

        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

        internal fun parse(file: JSONObject): Copy? {
            val id = file.optString("id")
            val name = file.optString("name")
            val size = file.optLong("size", -1)
            val md5 = file.optString("md5Checksum")
            if (!id.matches(ID) || !LocalBackupStore.isBackupName(name) || size !in 1..MAX_DOWNLOAD_BYTES ||
                !md5.matches(MD5) || file.optBoolean("trashed") || file.optString("mimeType") != "application/zip") {
                return null
            }
            val modified = runCatching { Instant.parse(file.optString("modifiedTime")) }.getOrNull() ?: return null
            return Copy(id, name, modified, size, md5.lowercase())
        }
    }
}
