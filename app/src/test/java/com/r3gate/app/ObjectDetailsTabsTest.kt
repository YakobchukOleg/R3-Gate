package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class ObjectDetailsTabsTest {
    @Test fun allTabsMatchPolishPrototype() {
        assertEquals(listOf("Przegląd","Zdjęcia","Materiały","Serwis","Techniczne"),
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

    @Test fun unsupportedServiceDoesNotInventServiceTickets() {
        val section = ObjectDetailsTabs.section(
            ObjectDetailsTabs.Tab.SERVICE, ObjectDetailsTabs.Counts(notes=9))
        assertTrue(section.description.contains("nie jest jeszcze dostępna"))
        assertEquals("Otwórz notatki", section.action)
    }

    @Test fun technicalSectionPreservesMapShortcut() {
        val section = ObjectDetailsTabs.section(
            ObjectDetailsTabs.Tab.TECHNICAL, ObjectDetailsTabs.Counts())
        assertEquals("Otwórz w Google Maps", section.action)
    }

    @Test fun overviewUsesExistingHistoryWithoutNavigationShortcut() {
        val section = ObjectDetailsTabs.section(
            ObjectDetailsTabs.Tab.OVERVIEW, ObjectDetailsTabs.Counts())
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
