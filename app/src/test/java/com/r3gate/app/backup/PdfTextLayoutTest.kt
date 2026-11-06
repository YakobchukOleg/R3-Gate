package com.r3gate.app.backup
import org.junit.Assert.*
import org.junit.Test

class PdfTextLayoutTest {
    private fun wrap(value: String, width: Float = 8f) = PdfTextLayout.wrap(value,width) { it.codePointCount(0,it.length).toFloat() }
    @Test fun shortCellIsUnchanged() { assertEquals(listOf("Łódź"),wrap("Łódź")) }
    @Test fun longNamesRetainEveryCharacter() {
        val value="Zespół Szkół Technicznych w Tczewie"
        assertEquals(value,wrap(value).joinToString("")); assertFalse(wrap(value).any { it.contains('…') })
    }
    @Test fun wrapsAtWordBoundariesWhenPossible() { assertEquals(listOf("Jana ","Stanisła","wskiego"),wrap("Jana Stanisławskiego")) }
    @Test fun longIdentifierIsNotTruncated() {
        val value="ABCDEFGHIJKLMNOPQRSTUVXYZ123456789"
        assertEquals(value,wrap(value).joinToString("")); assertTrue(wrap(value).all { it.length<=8 })
    }
    @Test fun explicitLineBreaksAndEmptyLinesArePreserved() { assertEquals(listOf("A","","B",""),wrap("A\r\n\r\nB\n")) }
    @Test fun emptyCellOccupiesOneLine() { assertEquals(listOf(""),wrap("")) }
    @Test fun unicodeSurrogatePairsAreNotSplit() {
        val value="A😀B😀C"; val lines=wrap(value,1f)
        assertEquals(value,lines.joinToString("")); assertTrue(lines.all { it.codePointCount(0,it.length)==1 })
    }
    @Test fun indivisibleWideGlyphStillProgresses() {
        assertEquals(listOf("ż","ą"),PdfTextLayout.wrap("żą",1f) { it.length*10f })
    }
    @Test fun invalidWidthIsRejected() {
        for(width in listOf(0f,-1f,Float.NaN,Float.POSITIVE_INFINITY)) {
            try { wrap("text",width); fail("Invalid width accepted") } catch (_:IllegalArgumentException) { }
        }
    }
    @Test fun invalidMeasurementIsRejected() {
        for(size in listOf(-1f,Float.NaN,Float.POSITIVE_INFINITY)) {
            try { PdfTextLayout.wrap("text",8f) { size }; fail("Invalid measurement accepted") } catch (_:IllegalArgumentException) { }
        }
    }
}
