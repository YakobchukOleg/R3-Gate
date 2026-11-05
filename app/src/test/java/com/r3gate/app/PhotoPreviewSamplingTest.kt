package com.r3gate.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoPreviewSamplingTest {
    @Test fun portraitAndLandscapeUseTheSameLongEdgeLimit() {
        assertEquals(4, PhotoPreview.sampleSize(4032, 3024, 1800))
        assertEquals(4, PhotoPreview.sampleSize(3024, 4032, 1800))
        assertEquals(1, PhotoPreview.sampleSize(1800, 900, 1800))
    }

    @Test fun oddEdgeDoesNotExceedPreviewLimitAfterRounding() {
        assertEquals(4, PhotoPreview.sampleSize(3601, 2000, 1800))
    }

    @Test fun failedBoundsAndInvalidLimitDoNotEnterDecodeLoop() {
        assertNull(PhotoPreview.sampleSize(-1, -1, 1800))
        assertNull(PhotoPreview.sampleSize(100, 0, 1800))
        assertNull(PhotoPreview.sampleSize(100, 100, 0))
    }

    @Test fun extremeDimensionsDoNotOverflowTheSampleSize() {
        assertEquals(1 shl 30, PhotoPreview.sampleSize(Int.MAX_VALUE, 1, 2))
        assertNull(PhotoPreview.sampleSize(Int.MAX_VALUE, 1, 1))
    }
}
