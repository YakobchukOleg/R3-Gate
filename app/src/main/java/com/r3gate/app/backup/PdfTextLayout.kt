package com.r3gate.app.backup

import java.text.BreakIterator
import java.util.Locale

/** Lossless line wrapping; glyph measurement is supplied by the PDF renderer. */
internal object PdfTextLayout {
    fun wrap(value: String, width: Float, measure: (String) -> Float): List<String> {
        require(width.isFinite() && width > 0) { "Invalid PDF column width" }
        return value.replace("\r\n", "\n").replace('\r', '\n').split('\n').flatMap { paragraph ->
            if (paragraph.isEmpty()) listOf("") else wrapParagraph(paragraph, width, measure)
        }
    }

    private fun wrapParagraph(value: String, width: Float, measure: (String) -> Float): List<String> {
        val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(value) }
        val result = mutableListOf<String>()
        var start = 0
        while (start < value.length) {
            var end = boundaries.following(start)
            var fitting = start
            var wordEnd = start
            while (end != BreakIterator.DONE) {
                val size = measure(value.substring(start, end))
                require(size.isFinite() && size >= 0) { "Invalid PDF glyph measurement" }
                if (size > width) break
                fitting = end
                if (value.substring(start, end).last().isWhitespace()) wordEnd = end
                end = boundaries.next()
            }
            // An indivisible oversized glyph still progresses and is scaled by the renderer.
            val next = when {
                fitting == start -> boundaries.following(start)
                fitting == value.length -> fitting
                wordEnd > start -> wordEnd
                else -> fitting
            }
            check(next > start) { "PDF wrapping did not progress" }
            result.add(value.substring(start, next))
            start = next
        }
        return result
    }
}
