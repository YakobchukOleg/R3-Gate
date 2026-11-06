package com.r3gate.app.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class PhotoRestoreCoordinatorTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)
    private var previous: String? = null
    private val madeStages = mutableListOf<java.io.File>()
    private val old = """{"objects":[{"id":"old"}]}"""
    private val restored = """{"objects":[{"id":"archived","archived":true}],
        "links":[{"objectId":"archived","photos":["content://source/one"]}]}"""

    @Before fun initialize() {
        previous = prefs.getString("data", null)
        assertTrue(prefs.edit().putString("data", old).commit())
    }
    @After fun restorePreferences() {
        madeStages.forEach { it.deleteRecursively() }
        assertTrue(prefs.edit().apply {
            if (previous == null) remove("data") else putString("data", previous)
        }.commit())
    }
    private fun archive(): ByteArray {
        val output = ByteArrayOutputStream()
        PhotoArchivePackage.write(restored, listOf("content://source/one"),
            { ByteArrayInputStream("photo-data".toByteArray()) }, output)
        return output.toByteArray()
    }
    private fun session(): PhotoRestoreCoordinator.Session {
        val bytes = archive()
        return PhotoRestoreCoordinator.prepare(context) { ByteArrayInputStream(bytes) }
            .also { madeStages.add(it.stagingDirectory) }
    }

    @Test fun confirmedRestoreKeepsPhotosReadable() {
        val task = session()
        assertEquals(1, task.photoCount)
        assertEquals(JournalRestoreStore.Outcome.RESTORED, task.commit(context, true))
        val result = org.json.JSONObject(prefs.getString("data", "{}")!!)
        assertEquals("archived", result.getJSONArray("objects").getJSONObject(0).getString("id"))
        assertTrue(result.getJSONArray("objects").getJSONObject(0).getBoolean("archived"))
        val photo = result.getJSONArray("links").getJSONObject(0).getJSONArray("photos").getString(0)
        assertTrue(photo.startsWith("content://com.r3gate.app.photos/"))
        assertEquals("photo-data", context.contentResolver.openInputStream(android.net.Uri.parse(photo))!!
            .bufferedReader().use { it.readText() })
    }

    @Test fun rejectedConfirmationKeepsJournal() {
        val task = session()
        try { task.commit(context, false); fail("Unconfirmed restore accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(old, prefs.getString("data", null))
        task.discard()
    }

    @Test fun concurrentJournalUpdateBlocksCommit() {
        val task = session()
        val edited = """{"objects":[{"id":"updated"}]}"""
        assertTrue(prefs.edit().putString("data", edited).commit())
        try { task.commit(context, true); fail("Stale restore accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(edited, prefs.getString("data", null))
        task.discard()
    }

    @Test fun changingStagedPhotoBlocksCommit() {
        val task = session()
        task.stagingDirectory.listFiles().orEmpty().single().writeText("modified")
        try { task.commit(context, true); fail("Tampered photo accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(old, prefs.getString("data", null))
        task.discard()
    }

    @Test fun cancelledRestoreCannotLaterCommit() {
        val task = session()
        task.discard()
        try { task.commit(context, true); fail("Disposed restore accepted") }
        catch (_: IllegalStateException) { }
        assertEquals(old, prefs.getString("data", null))
    }
    @Test fun deletedStagedPhotoCannotBeCommitted() {
        val task = session()
        task.stagingDirectory.listFiles().orEmpty().single().delete()
        try { task.commit(context, true); fail("Missing staged photo accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(old, prefs.getString("data", null))
        task.discard()
    }

    @Test fun legacyJournalWithoutPhotosCanBeRestored() {
        val journal = """{"objects":[{"id":"legacy","archived":true}]}"""
        val output = ByteArrayOutputStream()
        JournalSnapshot.write(journal, output)
        val bytes = output.toByteArray()
        val task = PhotoRestoreCoordinator.prepare(context) { ByteArrayInputStream(bytes) }
            .also { madeStages.add(it.stagingDirectory) }
        assertEquals(0, task.photoCount)
        assertEquals(JournalRestoreStore.Outcome.RESTORED, task.commit(context, true))
        assertEquals("legacy", org.json.JSONObject(prefs.getString("data", "{}")!!)
            .getJSONArray("objects").getJSONObject(0).getString("id"))
    }

    @Test fun confirmedRestoreImportsObjectGpsAndHistoricalDiagnosticsWithoutStartingGps() {
        val incoming="""{"objects":[{"id":"diagnostic-site","TAG":"DIA-01","city":"Test","Adres":"1"}]}"""
        val catalog=ObjectCatalog.export(incoming,mapOf("diagnostic-site" to """{"lat":54.0,"lon":18.0,"radius":100}"""))
        val bytes=ByteArrayOutputStream().also { output ->
            PhotoArchivePackage.write(incoming,emptyList(),{ error("No photos") },output,mapOf(
                BackupCompanions.OBJECTS to catalog.toByteArray(),
                BackupCompanions.DIAGNOSTICS to "{\"kind\":\"historic-test\"}\n".toByteArray()))
        }.toByteArray()
        prefs.edit().remove("gps_object_diagnostic-site").putBoolean("gps_enabled",false).commit()
        val task=PhotoRestoreCoordinator.prepare(context) { ByteArrayInputStream(bytes) }
        madeStages.add(task.stagingDirectory)
        try {
            task.commit(context,true)
            assertNull(task.attachmentWarning)
            val gps=org.json.JSONObject(prefs.getString("gps_object_diagnostic-site",null)!!)
            assertEquals(54.0,gps.getDouble("lat"),0.0)
            assertFalse(prefs.getBoolean("gps_enabled",true))
            assertTrue(java.io.File(context.filesDir,"diagnostics/imported.jsonl").readText().contains("historic-test"))
        } finally { prefs.edit().remove("gps_object_diagnostic-site").commit() }
    }
    @Test fun cancelledPreviewDoesNotRestoreGpsAttachment() {
        val incoming="""{"objects":[{"id":"cancel-site"}]}"""
        val catalog=ObjectCatalog.export(incoming,mapOf("cancel-site" to """{"lat":54.0,"lon":18.0}"""))
        val output=ByteArrayOutputStream();PhotoArchivePackage.write(incoming,emptyList(),{error("No photos")},output,
            mapOf(BackupCompanions.OBJECTS to catalog.toByteArray()))
        prefs.edit().remove("gps_object_cancel-site").commit()
        val task=PhotoRestoreCoordinator.prepare(context) { ByteArrayInputStream(output.toByteArray()) }
        task.discard()
        assertFalse(prefs.contains("gps_object_cancel-site"));assertEquals(old,prefs.getString("data",null))
    }
}
