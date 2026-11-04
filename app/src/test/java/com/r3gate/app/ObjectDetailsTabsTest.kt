package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class ObjectDetailsTabsTest {
    @Test fun allTabsMatchPolishPrototype() {
        assertEquals(listOf("Dzisiaj", "Zadania", "Zdjęcia", "Materiały", "Historia"),
            ObjectDetailsTabs.Tab.values().map { it.label })
    }

    @Test fun photoCountsAreDerivedFromStoredRecords() {
        val section = ObjectDetailsTabs.section(
            ObjectDetailsTabs.Tab.PHOTOS, ObjectDetailsTabs.Counts(photos=7))
        assertEquals("7 zdjęć przypisanych do obiektu", section.description)
        assertEquals("Otwórz zdjęcia", section.action)
    }

    @Test fun materialsShowRealCountAndAction() {
        val section = ObjectDetailsTabs.section(
            ObjectDetailsTabs.Tab.MATERIALS, ObjectDetailsTabs.Counts(materials=3))
        assertEquals("3 wpisów materiałowych", section.description)
        assertEquals("Otwórz materiały", section.action)
    }

    @Test fun unsupportedTasksOfferExistingNotesWithoutInventingTasks() {
        val section = ObjectDetailsTabs.section(
            ObjectDetailsTabs.Tab.TASKS, ObjectDetailsTabs.Counts(notes=9))
        assertTrue(section.description.contains("nie jest jeszcze dostępna"))
        assertEquals("Otwórz notatki", section.action)
    }

    @Test fun historyUsesExistingRecordsWithoutNavigationShortcut() {
        val section = ObjectDetailsTabs.section(ObjectDetailsTabs.Tab.HISTORY, ObjectDetailsTabs.Counts())
        assertEquals("Historia", section.heading)
        assertNull(section.action)
    }

    @Test fun rejectsNegativeCountsRatherThanDisplayingFalseValues() {
        assertThrows(IllegalArgumentException::class.java) {
            ObjectDetailsTabs.Counts(photos=-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ObjectDetailsTabs.Counts(materials=-2)
        }
    }

    @Test fun emptyRecordsShowZeroWithoutDemoContent() {
        assertEquals("0 zdjęć przypisanych do obiektu", ObjectDetailsTabs.section(
            ObjectDetailsTabs.Tab.PHOTOS, ObjectDetailsTabs.Counts()).description)
    }
}
