package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class PhotoRestoreJournalTest {
    private val journal = """{"objects":[{"id":"archived","archived":true}],
      "links":[{"id":"link1","objectId":"archived","photos":["content://old/a","content://old/b"]}],
      "materials":[{"id":"material1","batch":[{"photos":["content://old/a"]}]}],
      "notes":[{"text":"Zażółć gęślą jaźń"}]}"""

    @Test fun remapsOnlyPhotosAndPreservesHistoricalIDs() {
        val changed = PhotoRestoreJournal.remap(journal,
            mapOf("content://old/a" to "content://com.r3gate.app.photos/new/a",
                  "content://old/b" to "content://com.r3gate.app.photos/new/b"))
        val before = JSONObject(journal)
        val after = JSONObject(changed)
        assertEquals(before.getJSONArray("objects").toString(), after.getJSONArray("objects").toString())
        assertEquals("archived", after.getJSONArray("links").getJSONObject(0).getString("objectId"))
        assertEquals("content://com.r3gate.app.photos/new/a",
            after.getJSONArray("links").getJSONObject(0).getJSONArray("photos").getString(0))
        assertEquals("Zażółć gęślą jaźń",
            after.getJSONArray("notes").getJSONObject(0).getString("text"))
    }

    @Test fun refusesMissingPhotoMappings() {
        try {
            PhotoRestoreJournal.remap(journal,
                mapOf("content://old/a" to "content://new/a"))
            fail("Missing image accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun refusesUnusedOrUnsafeMappings() {
        try {
            PhotoRestoreJournal.remap("""{"objects":[]}""",
                mapOf("content://unused" to "content://new"))
            fail("Unused mapping accepted")
        } catch (_: IllegalArgumentException) { }
        try {
            PhotoRestoreJournal.remap("""{"links":[{"photos":["content://old/a"]}]}""",
                mapOf("content://old/a" to "file:///unsafe.jpg"))
            fail("Unsafe replacement accepted")
        } catch (_: IllegalArgumentException) { }
    }
}
