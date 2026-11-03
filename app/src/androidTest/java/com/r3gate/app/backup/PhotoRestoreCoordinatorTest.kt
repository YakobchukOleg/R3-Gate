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
}
