package com.r3gate.app
import org.junit.Assert.*
import org.junit.Test
class PhotoSendSelectionTest {
    @Test fun addingAPhotoDoesNotExtendAnAlreadyRequestedBatch() {
        assertEquals(listOf("one"),PhotoSendSelection.eligible(listOf("one","two"),setOf("one")))
    }
    @Test fun duplicatesAndRemovedPhotosCannotBeSentAgain() {
        assertEquals(listOf("two"),PhotoSendSelection.eligible(listOf("two","two",""),setOf("one","two","")))
    }
    @Test fun explicitNewRequestIncludesTheNewPhoto() {
        assertEquals(listOf("one","two"),PhotoSendSelection.eligible(listOf("one","two"),setOf("one","two")))
    }
}
