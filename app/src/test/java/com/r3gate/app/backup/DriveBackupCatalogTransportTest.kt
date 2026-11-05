package com.r3gate.app.backup

import com.r3gate.app.DriveStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

class DriveBackupCatalogTransportTest {
    private val name = "WorkMeter-SOLO-2026-11-04_20-10-00_00000000-0000-0000-0000-000000000001.wmb.zip"
    private fun file(id: String, at: String) = JSONObject().put("id", id).put("name", name)
        .put("modifiedTime", at).put("size", 42).put("mimeType", "application/zip")
        .put("md5Checksum", "0123456789abcdef0123456789abcdef")

    private class Response(private val body: JSONObject) :
        HttpURLConnection(URI("https://www.googleapis.com").toURL()) {
        override fun getResponseCode() = 200
        override fun getInputStream() = body.toString().byteInputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
    }

    @Test fun paginatesDeduplicatesAndSortsOnlySupportedBackups() {
        val old = file("old", "2026-11-01T00:00:00Z")
        val recent = file("recent", "2026-11-04T00:00:00Z")
        val pages = listOf(
            JSONObject().put("files", JSONArray().put(old)).put("nextPageToken", "a+b&c"),
            JSONObject().put("files", JSONArray().put(old).put(recent).put(
                file("other", "2026-11-05T00:00:00Z").put("name", "unrelated.zip")))
        ).iterator()
        var requests = 0
        val result = DriveBackupCatalog(DriveStore { endpoint ->
            if (++requests == 2) assertTrue(endpoint.endsWith("&pageToken=a%2Bb%26c"))
            Response(pages.next())
        }, "token").list()
        assertEquals(listOf("recent", "old"), result.map { it.id })
        assertEquals(2, requests)
    }

    @Test fun repeatedPageTokenStopsInsteadOfLooping() {
        var requests = 0
        val catalog = DriveBackupCatalog(DriveStore {
            requests++
            Response(JSONObject().put("nextPageToken", "same"))
        }, "token")
        assertThrows(IOException::class.java) { catalog.list() }
        assertEquals(2, requests)
    }

    @Test fun cancellationBetweenPagesStopsRequests() {
        var requests = 0
        val catalog = DriveBackupCatalog(DriveStore {
            requests++
            Response(JSONObject().put("nextPageToken", "next"))
        }, "token")
        var checks = 0
        assertThrows(InterruptedException::class.java) {
            catalog.list { if (++checks == 2) throw InterruptedException() }
        }
        assertEquals(1, requests)
    }

    @Test fun revalidationRejectsCopyChangedAfterSelection() {
        val original = file("selected", "2026-11-01T00:00:00Z")
        val selected = requireNotNull(DriveBackupCatalog.parse(original))
        val catalog = DriveBackupCatalog(DriveStore {
            Response(original.put("modifiedTime", "2026-11-05T00:00:00Z"))
        }, "token")
        assertThrows(IOException::class.java) { catalog.requireCurrent(selected) }
    }
}
