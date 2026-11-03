package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class WorkRecordListUiTest {
    @Test fun recognizesExistingListTypesWithoutInventingNewKeys() {
        assertEquals(WorkRecordListUi.Kind.PHOTOS,WorkRecordListUi.kind("links"))
        assertEquals(WorkRecordListUi.Kind.MATERIALS,WorkRecordListUi.kind("materials"))
        assertEquals(WorkRecordListUi.Kind.NOTES,WorkRecordListUi.kind("notes"))
        assertNull(WorkRecordListUi.kind("expenses"))
        assertNull(WorkRecordListUi.kind("unknown"))
    }

    @Test fun headersHavePolishCopyAndRealCount() {
        val photos=WorkRecordListUi.header(WorkRecordListUi.Kind.PHOTOS,2,false)
        val materials=WorkRecordListUi.header(WorkRecordListUi.Kind.MATERIALS,1,false)
        assertEquals("Zdjęcia i dokumentacja",photos.heading)
        assertEquals("2 wpisy",photos.countLabel)
        assertEquals("Zużyte materiały",materials.heading)
        assertEquals("1 wpis",materials.countLabel)
    }

    @Test fun countGrammarHandlesTeensAndMultiDigitCounts() {
        val cases=mapOf(0 to "0 wpisów",4 to "4 wpisy",11 to "11 wpisów",
            12 to "12 wpisów",22 to "22 wpisy",25 to "25 wpisów",101 to "101 wpisów")
        for((n,label) in cases) assertEquals(label,
            WorkRecordListUi.header(WorkRecordListUi.Kind.NOTES,n,false).countLabel)
    }

    @Test fun emptyStatesRespectFiltersAndRecordType() {
        assertEquals("Brak zdjęć. Dodaj pierwszą dokumentację.",
            WorkRecordListUi.header(WorkRecordListUi.Kind.PHOTOS,0,false).emptyMessage)
        assertEquals("Brak materiałów. Dodaj pierwszy wpis.",
            WorkRecordListUi.header(WorkRecordListUi.Kind.MATERIALS,0,false).emptyMessage)
        assertEquals("Brak wpisów dla wybranych filtrów.",
            WorkRecordListUi.header(WorkRecordListUi.Kind.MATERIALS,0,true).emptyMessage)
    }

    @Test fun linkedPhotoCardDoesNotClaimStoredImages() {
        val card=WorkRecordListUi.photos(0,"https://drive.google.com/file","Szkoła","")
        assertEquals("Link Google Drive",card.title)
        assertEquals("Link",card.badge)
        assertTrue(card.accessibilityLabel.contains("Szkoła"))
    }

    @Test fun photoUploadCardPreservesStatusAndCount() {
        val card=WorkRecordListUi.photos(3,"","Obiekt 3","Oczekuje na wysłanie")
        assertEquals("3 zdjęć",card.title)
        assertEquals("Oczekuje na wysłanie",card.badge)
        assertTrue(card.accessibilityLabel.contains("Obiekt 3"))
    }

    @Test fun materialCardShowsNameQuantityAndUnit() {
        val card=WorkRecordListUi.materials("Kabel","12","m","Tczew")
        assertEquals("Kabel",card.title)
        assertEquals("12 m",card.badge)
        assertEquals("Tczew",card.subtitle)
    }

    @Test fun missingDataUsesExplicitFallbacks() {
        assertEquals("Materiał",WorkRecordListUi.materials("","", "", "").title)
        assertEquals("Bez ilości",WorkRecordListUi.materials("","", "", "").badge)
        assertEquals("Bez obiektu",WorkRecordListUi.photos(1,"","","").subtitle)
    }

    @Test fun materialGroupUsesActualNumberOfRows() {
        val card=WorkRecordListUi.materialsGroup("03.11.2026","Szkoła",1)
        assertEquals("1 materiał",card.badge)
        assertEquals("03.11.2026",card.subtitle)
        assertEquals("3 materiałów",WorkRecordListUi.materialsGroup("","",3).badge)
    }

    @Test fun invalidCountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkRecordListUi.header(WorkRecordListUi.Kind.PHOTOS,-1,false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkRecordListUi.photos(-1,"","","")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkRecordListUi.materialsGroup("","",-1)
        }
    }
}
