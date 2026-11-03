package com.r3gate.app.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class PhotoRestoreStageTest {
    private val one = "content://photo/one"
    private val two = "content://photo/two"
    private fun archive(uris: List<String> = listOf(one, two)): ByteArray {
        val output = ByteArrayOutputStream()
        PhotoArchivePackage.write("""{"objects":[],"links":[]}""", uris,
            { uri -> ByteArrayInputStream(("binary-" + uri).toByteArray()) }, output)
        return output.toByteArray()
    }
    private fun withRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("stage-test-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    private inline fun rejected(block: () -> Unit) {
        try { block(); fail("Unsafe restore was accepted") }
        catch (_: Exception) { }
    }

    @Test fun stagesEveryPhotoAndPreservesBytes() = withRoot { root ->
        val bytes = archive()
        val stage = PhotoRestoreStage.prepare({ ByteArrayInputStream(bytes) }, listOf(one, two), root)
        assertEquals(2, stage.files.size)
        assertEquals("binary-$one", stage.files.getValue(one).readText())
        assertEquals("binary-$two", stage.files.getValue(two).readText())
        assertTrue(stage.files.values.all { it.parentFile == stage.directory })
        stage.discard()
        assertFalse(stage.directory.exists())
    }

    @Test fun noPhotoLegacyArchiveCanStageEmpty() = withRoot { root ->
        val output = ByteArrayOutputStream()
        JournalSnapshot.write("""{"objects":[]}""", output)
        val bytes = output.toByteArray()
        val stage = PhotoRestoreStage.prepare({ ByteArrayInputStream(bytes) }, emptyList(), root)
        assertTrue(stage.files.isEmpty())
        stage.discard()
    }

    @Test fun missingPhotoDoesNotLeaveStagingDirectory() = withRoot { root ->
        val bytes = archive(listOf(one))
        rejected { PhotoRestoreStage.prepare({ ByteArrayInputStream(bytes) }, listOf(one, two), root) }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun truncatedArchiveCannotStage() = withRoot { root ->
        val bytes = archive()
        rejected {
            PhotoRestoreStage.prepare({ ByteArrayInputStream(bytes.copyOf(bytes.size - 25)) },
                listOf(one, two), root)
        }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun changedArchiveSecondReadIsRejectedAndCleaned() = withRoot { root ->
        val good = archive()
        val changed = archive(listOf(one))
        var calls = 0
        rejected {
            PhotoRestoreStage.prepare({
                calls++
                ByteArrayInputStream(if (calls < 3) good else changed)
            }, listOf(one, two), root)
        }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun stagingNeverModifiesExistingFiles() = withRoot { root ->
        val untouched = File(root, "existing.txt").apply { writeText("important") }
        val bytes = archive()
        val stage = PhotoRestoreStage.prepare({ ByteArrayInputStream(bytes) }, listOf(one, two), root)
        assertEquals("important", untouched.readText())
        stage.discard()
        assertEquals("important", untouched.readText())
    }
}
