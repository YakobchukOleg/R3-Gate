package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WorkMeterArchiveTest {
    private fun make(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        WorkMeterArchive.write(output, entries.map { (name, value) ->
            WorkMeterArchive.Source(name) { ByteArrayInputStream(value.toByteArray(Charsets.UTF_8)) }
        })
        return output.toByteArray()
    }
    private inline fun fails(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }
    @Test fun emptyArchiveRoundTrip() {
        val result = WorkMeterArchive.inspect(ByteArrayInputStream(make()))
        assertEquals(1, result.version)
        assertTrue(result.files.isEmpty())
    }
    @Test fun roundTripWithPolishCharactersAndNestedFiles() {
        val archive = make("data/dziennik.json" to "{\"miasto\":\"Łódź, Gdańsk\"}", "photos/zażółć.txt" to "Zażółć gęślą jaźń")
        val result = WorkMeterArchive.inspect(ByteArrayInputStream(archive))
        assertEquals(2, result.files.size)
        assertEquals("photos/zażółć.txt", result.files[1].path)
        assertEquals(64, result.files[0].sha256.length)
    }
    @Test fun refusesZipSlipAndAbsolutePaths() {
        for (path in listOf("../secret", "/tmp/file", "a/../b", "a\\b", "x//y")) {
            fails { make(path to "data") }
        }
    }
    @Test fun refusesDuplicatePaths() {
        fails { make("data.json" to "one", "data.json" to "two") }
    }
    @Test fun refusesMissingManifest() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("data.json")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        fails { WorkMeterArchive.inspect(ByteArrayInputStream(out.toByteArray())) }
    }
    @Test fun detectsCorruptedOrIncompleteFile() {
        val archive = make("data.json" to "some essential records")
        fails { WorkMeterArchive.inspect(ByteArrayInputStream(archive.copyOf(archive.size / 3))) }
    }
    @Test fun rejectsUnsupportedVersion() {
        val original = make()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("workmeter-manifest.txt"))
            zip.write("WORKMETER-ARCHIVE/99\n".toByteArray())
            zip.closeEntry()
        }
        assertTrue(original.isNotEmpty())
        fails { WorkMeterArchive.inspect(ByteArrayInputStream(out.toByteArray())) }
    }
    @Test fun rejectsTamperedManifestDigest() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("data.json")); zip.write("hello".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("workmeter-manifest.txt"))
            val name = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("data.json".toByteArray())
            zip.write(("WORKMETER-ARCHIVE/1\n" + name + "\t5\t" + "0".repeat(64) + "\n").toByteArray())
            zip.closeEntry()
        }
        fails { WorkMeterArchive.inspect(ByteArrayInputStream(out.toByteArray())) }
    }
    @Test fun rejectsTruncatedCentralDirectoryAndEndRecord() {
        val archive = make("journal/data.json" to """{"objects":[]}""")
        fails { WorkMeterArchive.inspect(ByteArrayInputStream(archive.copyOf(archive.size - 10))) }
        fails { WorkMeterArchive.inspect(ByteArrayInputStream(archive.copyOf(archive.size - 22))) }
    }
    @Test fun fullArchiveStillReadsAfterTrailerVerification() {
        val archive = make("journal/data.json" to """{"name":"Zażółć gęślą jaźń"}""")
        val report = WorkMeterArchive.inspect(ByteArrayInputStream(archive))
        assertEquals(1, report.files.size)
        assertEquals("journal/data.json", report.files.single().path)
    }

    @Test fun rejectsCentralDirectoryCountTampering() {
        val bytes = make("journal/data.json" to """{"objects":[]}""")
        val tampered = bytes.copyOf()
        val endRecord = tampered.size - 22
        // ZIP end record claims an extra file that the actual stream does not contain.
        tampered[endRecord + 8] = 3
        tampered[endRecord + 10] = 3
        fails { WorkMeterArchive.inspect(ByteArrayInputStream(tampered)) }
    }

    @Test fun rejectsCentralDirectoryOffsetTampering() {
        val bytes = make("journal/data.json" to "test")
        val tampered = bytes.copyOf()
        val endRecord = tampered.size - 22
        tampered[endRecord + 16] = (tampered[endRecord + 16].toInt() xor 1).toByte()
        fails { WorkMeterArchive.inspect(ByteArrayInputStream(tampered)) }
    }

}
