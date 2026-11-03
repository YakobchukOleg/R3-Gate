package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class PhotoPreviewUiTest {
    @Test fun previewLabelsIncludePositionAndTotal() {
        assertEquals("Zdjęcie 1 z 3",PhotoPreviewUi.state(0,3,true).heading)
        assertEquals("Zdjęcie 3 z 3",PhotoPreviewUi.state(2,3,true).heading)
    }

    @Test fun availablePreviewOffersOpeningAction() {
        val state=PhotoPreviewUi.state(1,3,true)
        assertTrue(state.accessibilityLabel.contains("Otwórz podgląd"))
        assertNull(state.message)
    }

    @Test fun missingPhotoHasAccessibleUnavailableState() {
        val state=PhotoPreviewUi.state(0,2,false)
        assertTrue(state.accessibilityLabel.contains("Niedostępne na urządzeniu"))
        assertEquals("Oryginał zdjęcia nie jest dostępny na tym urządzeniu",state.message)
    }

    @Test fun summaryCountsPhotosWithoutClaimingUploadSuccess() {
        assertEquals("Brak zdjęć",PhotoPreviewUi.summary(0))
        assertEquals("1 zdjęcie",PhotoPreviewUi.summary(1))
        assertEquals("5 zdjęć",PhotoPreviewUi.summary(5))
    }

    @Test fun rejectsInvalidIndexOrTotal() {
        assertThrows(IllegalArgumentException::class.java) { PhotoPreviewUi.state(0,0,true) }
        assertThrows(IllegalArgumentException::class.java) { PhotoPreviewUi.state(-1,2,true) }
        assertThrows(IllegalArgumentException::class.java) { PhotoPreviewUi.state(2,2,true) }
        assertThrows(IllegalArgumentException::class.java) { PhotoPreviewUi.summary(-1) }
    }

    @Test fun statusNeverExposesUnderlyingImageUri() {
        val state=PhotoPreviewUi.state(0,1,false)
        assertFalse(state.accessibilityLabel.contains("content://"))
        assertFalse(state.accessibilityLabel.contains("file://"))
    }
}
