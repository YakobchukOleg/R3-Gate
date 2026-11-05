package com.r3gate.app

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ObjectListPanelInstrumentedTest {
    @Before fun seed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pin = MessageDigest.getInstance("SHA-256")
            .digest("test1234".toByteArray()).joinToString("") { "%02x".format(it) }
        val active = JSONObject().put("id", "active-1").put("TAG", "TCZ-01")
            .put("city", "Tczew").put("Adres", "Główna 3")
            .put("organization", "Serwis")
        val archive = JSONObject().put("id", "archived-1").put("TAG", "GDA-02")
            .put("city", "Gdańsk").put("Adres", "Długa 5")
            .put("organization", "Szkoła").put("archived", true)
        val data = JSONObject().put("schemaVersion", 2)
            .put("objects", JSONArray().put(active).put(archive))
        context.getSharedPreferences("r3_gate", 0).edit().clear()
            .putString("salt", "test").putString("pin", pin)
            .putString("data", data.toString()).commit()
    }

    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else emptyList()

    private fun views(activity: Activity) = views(activity.window.decorView)
    private fun texts(activity: Activity) =
        views(activity).filterIsInstance<TextView>().map { it.text.toString() }

    private fun goToObjects(activity: Activity) {
        val button = views(activity).filterIsInstance<Button>()
            .firstOrNull { it.contentDescription == "Obiekty" }
        assertNotNull("Permanent Obiekty navigation should exist", button)
        button!!.performClick()
        assertEquals(1, views(activity).filterIsInstance<ObjectListPanel>().size)
    }

    @Test fun opensRealObjectCardWithoutChangingDomainData() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                goToObjects(activity)
                assertTrue(texts(activity).any { it.contains("TCZ-01") })
                val card = views(activity).firstOrNull {
                    it.contentDescription?.toString()?.contains("TCZ-01") == true && it.isClickable
                }
                assertNotNull("Object card should be clickable", card)
                card!!.performClick()
                assertTrue(texts(activity).contains("Edytuj obiekt"))
            }
        }
    }

    @Test fun filtersArchivedRecordsWithoutDeletingHistory() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                goToObjects(activity)
                assertTrue(texts(activity).any { it.contains("TCZ-01") })
                val archiveToggle = views(activity).filterIsInstance<TextView>()
                    .firstOrNull { it.text.toString() == "Wszystkie" && it.isClickable }
                assertNotNull("Archive filter", archiveToggle)
                archiveToggle!!.performClick()
                assertTrue(texts(activity).any { it.contains("GDA-02") })
                assertFalse(texts(activity).any { it.contains("TCZ-01") })
                val preferences = activity.getSharedPreferences("r3_gate", 0)
                assertTrue(preferences.getBoolean("archived_objects", false))
                val json = JSONObject(preferences.getString("data", "{}")!!)
                assertEquals(2, json.getJSONArray("objects").length())
            }
        }
    }
    @Test fun typingSearchFiltersRealScreenWithoutChangingStorage() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                goToObjects(activity)
                val search = views(activity).filterIsInstance<android.widget.EditText>()
                    .firstOrNull { it.contentDescription == "Szukaj obiektu: miasto, firma, nazwa, ulica lub adres" }
                assertNotNull("Search field", search)
                search!!.setText("nieznany-adres")
                assertTrue(texts(activity).contains("Brak obiektów dla wybranych filtrów."))
                search.setText("tczew")
                assertTrue(texts(activity).any { it.contains("TCZ-01") })
                assertFalse(texts(activity).any { it.contains("GDA-02") })
                val data = activity.getSharedPreferences("r3_gate", 0).getString("data", "{}")
                assertEquals(2, JSONObject(data!!).getJSONArray("objects").length())
            }
        }
    }

    @Test fun objectFiltersSurviveOpeningDetailsAndRecreatingTheActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                goToObjects(activity)
                views(activity).filterIsInstance<android.widget.EditText>()
                    .first { it.contentDescription == "Szukaj obiektu: miasto, firma, nazwa, ulica lub adres" }.setText("tczew")
                views(activity).first { it.contentDescription == "Wyłącz grupowanie" }.performClick()
                views(activity).first { it.isClickable && it.contentDescription?.contains("TCZ-01") == true }.performClick()
                activity.onBackPressed()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val search = views(activity).filterIsInstance<android.widget.EditText>()
                    .first { it.contentDescription == "Szukaj obiektu: miasto, firma, nazwa, ulica lub adres" }
                assertEquals("tczew", search.text.toString())
                assertTrue(views(activity).any { it.contentDescription == "Grupuj według miast" })
            }
        }
    }

    @Test fun cityFilterIsInteractiveOnActualScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                goToObjects(activity)
                val cityChip = views(activity).filterIsInstance<TextView>()
                    .firstOrNull { it.contentDescription == "Filtr miasta: Tczew" }
                assertNotNull("Tczew filter", cityChip)
                cityChip!!.performClick()
                assertTrue(texts(activity).any { it.contains("TCZ-01") })
                assertTrue(texts(activity).any { it.contains("1 obiekt") })
                assertFalse(texts(activity).any { it.contains("GDA-02") })
            }
        }
    }

    private fun openActiveDetails(activity: Activity) {
        goToObjects(activity)
        val card = views(activity).firstOrNull {
            it.contentDescription?.toString()?.contains("TCZ-01") == true && it.isClickable
        }
        assertNotNull("Object card should be clickable", card)
        card!!.performClick()
    }

    private fun openObjectRecords(activity: Activity, label: String) {
        views(activity).filterIsInstance<Button>().single { it.contentDescription == "Zakładka: $label" }.performClick()
        views(activity).filterIsInstance<Button>().single { it.text.toString() == "Otwórz ${label.lowercase()}" }.performClick()
    }

    @Test fun multilineMapButtonFitsTheObjectActionRow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                val panel = views(activity).filterIsInstance<ObjectDetailsPanel>().single()
                val width = (260 * activity.resources.displayMetrics.density).toInt()
                panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                panel.layout(0, 0, width, panel.measuredHeight)
                val buttons = views(activity).filterIsInstance<Button>()
                val edit = buttons.single { it.text == "Edytuj obiekt" }
                val maps = buttons.single { it.text == "Otwórz w Google Maps" }
                assertTrue(maps.lineCount > 1)
                assertEquals(edit.top, maps.top)
                assertEquals(edit.bottom, maps.bottom)
                assertTrue(maps.bottom <= (maps.parent as View).height)
            }
        }
    }

    @Test fun nativeDetailsShowsMetadataAndEmptyHistory() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                assertEquals(1, views(activity).filterIsInstance<ObjectDetailsPanel>().size)
                assertTrue(texts(activity).contains("Główna 3").not()) // Location includes the city
                assertTrue(texts(activity).any { it.contains("Tczew, Główna 3") })
                assertTrue(texts(activity).contains("Czas na obiekcie"))
                assertTrue(texts(activity).contains("Brak zarejestrowanego czasu na tym obiekcie."))
                assertTrue(texts(activity).contains("Ważne informacje"))
                assertTrue(texts(activity).contains("Otwórz w Google Maps"))
            }
        }
    }

    @Test fun nativeDetailsQuickActionsNavigateToExistingScreens() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                val materials = views(activity).filterIsInstance<Button>()
                    .firstOrNull { it.text.toString() == "Materiały" }
                assertNotNull("Materials shortcut", materials)
                materials!!.performClick()
                assertTrue(texts(activity).any { it.contains("Materiały") })
            }
        }
    }

    @Test fun photoAndMaterialTabsShowActualEmptyCounts() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                val photos = views(activity).filterIsInstance<Button>()
                    .first { it.contentDescription == "Zakładka: Zdjęcia" }
                photos.performClick()
                assertTrue(texts(activity).contains("0 zdjęć przypisanych do obiektu"))
                assertTrue(texts(activity).contains("Otwórz zdjęcia"))
                val materials = views(activity).filterIsInstance<Button>()
                    .first { it.contentDescription == "Zakładka: Materiały" }
                materials.performClick()
                assertTrue(texts(activity).contains("0 wpisów materiałowych"))
                assertTrue(texts(activity).contains("Otwórz materiały"))
            }
        }
    }

    @Test fun tasksTabShowsFallbackAndNotesAction() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                val service = views(activity).filterIsInstance<Button>()
                    .firstOrNull { it.contentDescription == "Zakładka: Zadania" }
                assertNotNull(service)
                service!!.performClick()
                assertTrue(texts(activity).any {
                    it.contains("Lista zadań i zgłoszeń nie jest jeszcze dostępna")
                })
                assertTrue(texts(activity).contains("Otwórz notatki"))
                val notes = views(activity).filterIsInstance<Button>()
                    .first { it.text.toString() == "Otwórz notatki" }
                notes.performClick()
                assertTrue(texts(activity).any { it.contains("notatek") })
            }
        }
    }

    @Test fun todayTabKeepsTechnicalDetailsAndMap() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                val tech = views(activity).filterIsInstance<Button>()
                    .first { it.contentDescription == "Zakładka: Dzisiaj" }
                tech.performClick()
                assertTrue(texts(activity).contains("TAG: TCZ-01"))
                assertTrue(texts(activity).contains("Otwórz w Google Maps"))
            }
        }
    }

    @Test fun newObjectFormShowsSectionsAndRequiredHints() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                goToObjects(activity)
                val addButton=views(activity).filterIsInstance<Button>()
                    .firstOrNull { it.contentDescription == "Dodaj obiekt" && it.isClickable }
                assertNotNull("Native add-object action", addButton)
                val minTouchSize = (48 * activity.resources.displayMetrics.density + 0.5f).toInt()
                assertTrue(addButton!!.minimumHeight >= minTouchSize)
                assertTrue(addButton.minimumWidth >= minTouchSize)
                addButton.performClick()
                val labels=texts(activity)
                assertTrue(labels.contains("Nowy obiekt"))
                assertTrue(labels.contains("Lokalizacja"))
                assertTrue(labels.contains("Informacje o obiekcie"))
                assertTrue(labels.contains("Miasto *"))
                assertTrue(labels.contains("Adres *"))
                assertTrue(labels.contains("* Pola wymagane"))
                assertEquals(6, views(activity).filterIsInstance<android.widget.EditText>().size)
            }
        }
    }

    @Test fun editObjectFormShowsImmutableTagAndHistoryNotice() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                val edit=views(activity).filterIsInstance<Button>()
                    .firstOrNull { it.text.toString()=="Edytuj obiekt" }
                assertNotNull("Edit existing object", edit)
                edit!!.performClick()
                val labels=texts(activity)
                assertTrue(labels.contains("Edytuj obiekt"))
                assertTrue(labels.any { it.contains("TAG: TCZ-01 (stały)") })
                assertTrue(labels.any { it.contains("Historia pracy pozostaje zachowana") })
                assertEquals(5, views(activity).filterIsInstance<android.widget.EditText>().size)
            }
        }
    }

    @Test fun materialsFormShowsAssignmentQuantityAndBatchSections() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                openObjectRecords(activity, "Materiały")
                val add=views(activity).filterIsInstance<Button>()
                    .first { it.text.toString() in listOf("+ Dodaj","Dodaj zdjęcie") }
                add.performClick()
                val labels=texts(activity)
                assertTrue(labels.contains("Materiał na obiekcie"))
                assertTrue(labels.contains("Przypisanie"))
                assertTrue(labels.contains("Materiał i ilość"))
                assertTrue(labels.contains("Materiały w zestawie: 0"))
                assertTrue(labels.contains("Brak dołączonych zdjęć"))
            }
        }
    }

    @Test fun photoFormShowsAttachmentGuidanceWithoutFakePhotos() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                openObjectRecords(activity, "Zdjęcia")
                val add=views(activity).filterIsInstance<Button>()
                    .first { it.text.toString() in listOf("+ Dodaj","Dodaj zdjęcie") }
                add.performClick()
                val labels=texts(activity)
                assertTrue(labels.contains("Zdjęcia i dokumentacja"))
                assertTrue(labels.contains("Załączniki"))
                assertTrue(labels.contains("Brak dołączonych zdjęć"))
                assertTrue(labels.contains("Dodaj zdjęcia z galerii"))
            }
        }
    }

    @Test fun emptyPhotoListShowsHeaderAndHelpfulEmptyState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                openObjectRecords(activity, "Zdjęcia")
                assertTrue(texts(activity).contains("Zdjęcia i dokumentacja"))
                assertTrue(texts(activity).contains("0 wpisów"))
                assertTrue(texts(activity).contains("Brak zdjęć. Dodaj pierwszą dokumentację."))
            }
        }
    }

    @Test fun emptyMaterialListShowsCountAndHelpfulEmptyState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                openObjectRecords(activity, "Materiały")
                assertTrue(texts(activity).contains("Zużyte materiały"))
                assertTrue(texts(activity).contains("0 wpisów"))
                assertTrue(texts(activity).contains("Brak materiałów. Dodaj pierwszy wpis."))
            }
        }
    }

    @Test fun linkedPhotoRecordUsesNativeCardWithRealData() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val pref=context.getSharedPreferences("r3_gate",0)
        val data=JSONObject(pref.getString("data","{}")!!)
        data.put("links",JSONArray().put(JSONObject()
            .put("id","link-1").put("objectId","active-1")
            .put("date","2026-11-03")
            .put("Link Google Drive","https://drive.google.com/file/d/test")))
        pref.edit().putString("data",data.toString()).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                openObjectRecords(activity, "Zdjęcia")
                assertTrue(texts(activity).contains("1 wpis"))
                assertEquals(1,views(activity).filterIsInstance<WorkRecordListCard>().size)
                assertTrue(views(activity).any {
                    it.contentDescription?.toString()?.contains("Link Google Drive") == true &&
                        it is WorkRecordListCard
                })
            }
        }
    }

    @Test fun materialRecordUsesNativeGroupCardWithoutMutatingData() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val pref=context.getSharedPreferences("r3_gate",0)
        val data=JSONObject(pref.getString("data","{}")!!)
        data.put("materials",JSONArray().put(JSONObject()
            .put("id","mat-1").put("objectId","active-1")
            .put("date","2026-11-03").put("name","Kabel")
            .put("quantity","2").put("unit","m").put("storage","object")))
        pref.edit().putString("data",data.toString()).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                openObjectRecords(activity, "Materiały")
                assertTrue(texts(activity).contains("1 wpis"))
                assertEquals(1,views(activity).filterIsInstance<WorkRecordListCard>().size)
                val card=views(activity).filterIsInstance<WorkRecordListCard>().single()
                assertTrue(card.contentDescription.toString().contains("1 materiał"))
                val stored=JSONObject(pref.getString("data","{}")!!)
                assertEquals(1,stored.getJSONArray("materials").length())
            }
        }
    }

    @Test fun uploadedPhotoWithoutPreviewIsCountedAndOnlyPendingPhotoShowsError() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val pref=context.getSharedPreferences("r3_gate",0)
        val data=JSONObject(pref.getString("data","{}")!!)
        data.put("links",JSONArray().put(JSONObject()
            .put("id","missing-photo").put("objectId","active-1")
            .put("date","2026-11-03")
            .put("photos",JSONArray().put("content://com.r3gate.app/not-existing-photo").put("file:///sent.jpg"))
            .put("driveFiles", JSONObject().put("account|file:///sent.jpg", "drive-file-id"))))
        pref.edit().putString("data",data.toString()).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                openActiveDetails(activity)
                openObjectRecords(activity, "Zdjęcia")
                val card=views(activity).filterIsInstance<WorkRecordListCard>().single()
                card.performClick()
                assertTrue(texts(activity).contains("Wysłano na Google Drive: 1 z 2"))
                assertFalse(texts(activity).contains("Zdjęcie 2 z 2"))
                val placeholder=views(activity).filterIsInstance<TextView>()
                    .firstOrNull { it.contentDescription?.toString()?.contains("Niedostępne na urządzeniu")==true }
                assertNotNull("Missing photo should expose accessible fallback",placeholder)
                assertTrue(texts(activity).contains("Oryginał zdjęcia nie jest dostępny na tym urządzeniu"))
                assertEquals(1,JSONObject(pref.getString("data","{}")!!).getJSONArray("links").length())
            }
        }
    }

    @Test fun selectedCityIsClearedWhenSwitchingToArchive() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                goToObjects(activity)
                val chip=views(activity).filterIsInstance<TextView>()
                    .first { it.contentDescription=="Filtr miasta: Tczew" }
                chip.performClick()
                assertTrue(views(activity).filterIsInstance<TextView>()
                    .any { it.contentDescription=="Filtr miasta: Tczew" && it.isSelected })
                val archive=views(activity).filterIsInstance<TextView>()
                    .first { it.contentDescription=="Pokaż archiwum obiektów" }
                archive.performClick()
                val all=views(activity).filterIsInstance<TextView>()
                    .first { it.contentDescription=="Wszystkie miasta" }
                assertTrue(all.isSelected)
                assertTrue(texts(activity).any { it.contains("GDA-02") })
            }
        }
    }

    @Test fun cityFilteringCanBeClearedByAllCitiesChip() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                goToObjects(activity)
                views(activity).filterIsInstance<TextView>()
                    .first { it.contentDescription=="Filtr miasta: Tczew" }.performClick()
                val all=views(activity).filterIsInstance<TextView>()
                    .first { it.contentDescription=="Wszystkie miasta" }
                all.performClick()
                assertTrue(all.isSelected || views(activity).filterIsInstance<TextView>()
                    .any { it.contentDescription=="Wszystkie miasta" && it.isSelected })
                assertTrue(texts(activity).any { it.contains("TCZ-01") })
            }
        }
    }

}
