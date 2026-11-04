package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

class PhotoArchiveIndexTest {
    private inline fun rejected(action: () -> Unit) {
        try { action(); fail("Invalid photo index accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun emptyIndexRoundTrips() {
        assertTrue(PhotoArchiveIndex.decode(PhotoArchiveIndex.encode(emptyList())).isEmpty())
    }

    @Test fun preservesUnicodeUriAndDeterministicPath() {
        val uri = "content://photos/Łódź/zażółć"
        val list = PhotoArchiveIndex.build(listOf(uri))
        assertEquals(list, PhotoArchiveIndex.decode(PhotoArchiveIndex.encode(list)))
        assertEquals(75, list[0].path.length)
        assertEquals(list[0].path, PhotoArchiveIndex.pathFor(uri))
        assertFalse(list[0].path.contains("Łódź"))
    }

    @Test fun removesDuplicateReferencesWithoutChangingOrder() {
        val refs = PhotoArchiveIndex.build(listOf("content://a", "content://b", "content://a"))
        assertEquals(listOf("content://a", "content://b"), refs.map { it.uri })
    }

    @Test fun rejectsBlankAndControlCharacterUris() {
        rejected { PhotoArchiveIndex.build(listOf(" ")) }
        rejected { PhotoArchiveIndex.build(listOf("content://a\nentry")) }
    }

    @Test fun rejectsMalformedAndTruncatedIndexes() {
        rejected { PhotoArchiveIndex.decode("BROKEN/1\n".toByteArray()) }
        rejected { PhotoArchiveIndex.decode("WORKMETER-PHOTOS/1".toByteArray()) }
        rejected { PhotoArchiveIndex.decode("WORKMETER-PHOTOS/1\n!\tphotos/b.bin\n".toByteArray()) }
    }

    @Test fun rejectsTamperedPathsAndDuplicateMappings() {
        val original = PhotoArchiveIndex.encode(PhotoArchiveIndex.build(listOf("content://a")))
            .toString(Charsets.UTF_8)
        rejected { PhotoArchiveIndex.decode(original.replace(".bin", ".jpg").toByteArray()) }
        rejected { PhotoArchiveIndex.decode((original + original.substringAfter('\n')).toByteArray()) }
    }

    @Test fun rejectsTooManyPhotos() {
        rejected {
            PhotoArchiveIndex.build((0..PhotoArchiveIndex.MAX_PHOTOS).map { "content://$it" })
        }
    }

    @Test fun rejectsMalformedUtf8() {
        rejected { PhotoArchiveIndex.decode(byteArrayOf(0xc3.toByte(), 0x28)) }
    }
}
