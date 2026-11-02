package com.r3gate.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * QA-only persistence regressions. These tests use synthetic records and never
 * modify production data or the application implementation.
 */
@RunWith(AndroidJUnit4::class)
class LocalDataPersistenceRegressionTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("r3_gate", Context.MODE_PRIVATE)

    private fun objectRow() = JSONObject()
        .put("id", "qa-object-1").put("TAG", "QA-01")
        .put("city", "Testowo").put("Adres", "1")

    private fun seed(data: JSONObject) {
        val pin = MessageDigest.getInstance("SHA-256")
            .digest("test1234".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertTrue(prefs.edit().clear().putString("salt", "test")
            .putString("pin", pin).putString("data", data.toString()).commit())
    }

    private fun stored() = JSONObject(prefs.getString("data", "{}")!!)

    @Before fun setup() {
        seed(JSONObject().put("schemaVersion", 2)
            .put("objects", JSONArray().put(objectRow())))
    }

    @After fun cleanup() {
        prefs.edit().clear().commit()
    }

    @Test fun activityRecreationPreservesIndependentCollectionsAndIds() {
        val data = stored()
            .put("notes", JSONArray().put(JSONObject()
                .put("id", "qa-note-1").put("text", "Keep offline note")))
            .put("materials", JSONArray().put(JSONObject()
                .put("id", "qa-material-1").put("name", "Cable").put("quantity", 12.5)))
            .put("shifts", JSONArray().put(JSONObject()
                .put("id", "qa-shift-1").put("start", 1793606400000L)
                .put("end", 1793642400000L)))
        seed(data)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            val result = stored()
            assertEquals("qa-object-1", result.getJSONArray("objects").getJSONObject(0).getString("id"))
            assertEquals("qa-note-1", result.getJSONArray("notes").getJSONObject(0).getString("id"))
            assertEquals("qa-material-1", result.getJSONArray("materials").getJSONObject(0).getString("id"))
            assertEquals(12.5, result.getJSONArray("materials").getJSONObject(0).getDouble("quantity"), 0.00001)
            assertEquals("qa-shift-1", result.getJSONArray("shifts").getJSONObject(0).getString("id"))
        }
    }

    @Test fun repeatedActivityLaunchDoesNotDuplicateSavedEntries() {
        val data = stored()
            .put("notes", JSONArray().put(JSONObject()
                .put("id", "qa-note-1").put("text", "One note")))
            .put("shifts", JSONArray().put(JSONObject()
                .put("id", "qa-shift-1").put("start", 1793606400000L)
                .put("end", 1793610000000L)))
        seed(data)

        repeat(3) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.recreate()
            }
        }
        val result = stored()
        assertEquals(1, result.getJSONArray("objects").length())
        assertEquals(1, result.getJSONArray("notes").length())
        assertEquals(1, result.getJSONArray("shifts").length())
        assertEquals("qa-note-1", result.getJSONArray("notes").getJSONObject(0).getString("id"))
    }

    @Test fun unknownFieldsInCurrentSchemaSurviveActivityRestart() {
        val nested = JSONObject().put("nested", JSONArray().put("alpha").put(42))
        seed(stored().put("qaFutureField", nested)
            .put("objects", JSONArray().put(objectRow().put("customMetadata", "preserve-me"))))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
        }
        val result = stored()
        assertEquals("preserve-me", result.getJSONArray("objects")
            .getJSONObject(0).getString("customMetadata"))
        assertEquals(42, result.getJSONObject("qaFutureField")
            .getJSONArray("nested").getInt(1))
    }
}
