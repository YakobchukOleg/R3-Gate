package com.r3gate.app.backup
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class ObjectCatalogTest {
    private val journal="""{"objects":[{"id":"a","TAG":"TCZ-01","city":"Tczew","Adres":"Test 1","placeType":"WORKSITE","notes":"secret","photos":["secret.jpg"],"materials":["secret"]}],"shifts":[{"id":"private"}],"notes":["private"],"payments":[100]}"""
    private fun exported()=ObjectCatalog.export(journal,mapOf("a" to """{"lat":54.1,"lon":18.2,"radius":100,"accuracy":5,"time":123}"""))
    @Test fun exportContainsOnlyWhitelistedFieldsAndGps() {
        val text=exported();assertFalse(text.contains("secret"));assertFalse(text.contains("payments"));assertFalse(text.contains("shifts"));assertFalse(text.contains("accuracy"));assertFalse(text.contains("photos"))
        assertEquals(54.1,ObjectCatalog.rows(text).single().getJSONObject("gps").getDouble("lat"),0.0)
    }
    @Test fun importingAddsGpsAndPreservesUnrelatedJournal() {
        val result=ObjectCatalog.merge("""{"objects":[],"notes":["retain"],"future":7}""",exported())
        assertEquals(1,result.added);assertEquals("retain",JSONObject(result.journal).getJSONArray("notes").getString(0));assertEquals(7,JSONObject(result.journal).getInt("future"));assertTrue(result.locations.containsKey("a"))
    }
    @Test fun sameIdIsSkippedWithoutChangingExistingNotesOrGps() {
        val result=ObjectCatalog.merge(journal,exported());assertEquals(0,result.added);assertEquals(1,result.skipped);assertTrue(result.locations.isEmpty());assertTrue(result.journal.contains("secret"))
    }
    @Test fun conflictingTagAbortsImport() {
        assertThrows(IllegalArgumentException::class.java) { ObjectCatalog.merge(journal.replace("\"id\":\"a\"","\"id\":\"b\""),exported()) }
    }
    @Test fun rejectsInvalidGpsInsteadOfSilentlyRelocatingObject() {
        assertThrows(IllegalArgumentException::class.java) { ObjectCatalog.rows(exported().replace("54.1","154.1")) }
    }
    @Test fun rejectsUnknownSchema() { assertThrows(IllegalArgumentException::class.java) { ObjectCatalog.rows(exported().replace("\"version\":1","\"version\":99")) } }
    @Test fun importStripsInjectedNotesAndPhotos() {
        val input=JSONObject(exported());input.getJSONArray("objects").getJSONObject(0).put("notes","injected").put("photos","injected")
        val result=ObjectCatalog.merge("{}",input.toString());assertFalse(result.journal.contains("injected"))
    }
    @Test fun legacyMinimalObjectDoesNotBreakFullBackup() {
        val text=ObjectCatalog.export("""{"objects":[{"id":"legacy"}]}""",emptyMap());assertEquals(1,ObjectCatalog.rows(text).size)
    }
}
