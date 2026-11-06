package com.r3gate.app.backup

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * Offline Polish PDF report. Uses Android PdfDocument; no third-party dependencies.
 * Paging is performed without caching all report rows or embedding user photographs.
 */
object WorkMeterPdf {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 36f
    private const val BOTTOM = 794f
    private const val LINE = 15f

    fun write(
        destination: OutputStream,
        title: String,
        headings: List<String>,
        rows: Sequence<List<String>>,
        footer: String = "WorkMeter SOLO"
    ) {
        require(title.isNotBlank() && headings.isNotEmpty()) { "Missing PDF title or headings" }
        require(headings.size <= 8) { "Too many PDF columns" }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = 9f
        }
        val bold = Paint(text).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = 10f
        }
        var page: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var number = 0
        var y = 0f
        val usable = WIDTH - 2 * MARGIN
        val col = usable / headings.size

        fun wrapped(value: String, paint: Paint, width: Float) =
            PdfTextLayout.wrap(value, width, paint::measureText)
        val titlePaint = Paint(bold).apply { textSize = 17f }
        val titleLines = wrapped(title, titlePaint, usable)
        val headerLines = headings.map { wrapped(it, bold, col - 9) }
        val headerHeight = titleLines.size * 22f + 36f + headerLines.maxOf { it.size } * LINE
        require(MARGIN + headerHeight + LINE <= BOTTOM) { "PDF header leaves no room for data" }
        require(wrapped("$footer  •  Strona 999999", text, usable).size <= 2) { "PDF footer is too long" }

        val pdf = PdfDocument()

        fun draw(value: String, x: Float, baseline: Float, paint: Paint, width: Float) {
            val size = paint.measureText(value)
            val oldScale = paint.textScaleX
            if (size > width) paint.textScaleX = oldScale * width / size
            try { canvas!!.drawText(value, x, baseline, paint) }
            finally { paint.textScaleX = oldScale }
        }
        fun endPage() {
            val current = page ?: return
            val lines = wrapped("$footer  •  Strona $number", text, usable)
            lines.forEachIndexed { index, line ->
                draw(line, MARGIN, HEIGHT - 25f - (lines.size - 1 - index) * 10f, text, usable)
            }
            pdf.finishPage(current)
            page = null
        }
        fun startPage() {
            endPage()
            number++
            val current = pdf.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number).create())
            page = current
            canvas = current.canvas
            y = MARGIN + 15
            for (line in titleLines) { draw(line, MARGIN, y, titlePaint, usable); y += 22f }
            y += 6f
            val count = headerLines.maxOf { it.size }
            for (line in 0 until count) {
                headerLines.forEachIndexed { index, cell ->
                    cell.getOrNull(line)?.let { draw(it, MARGIN + index * col, y, bold, col - 9) }
                }
                y += LINE
            }
            canvas!!.drawLine(MARGIN, y - 4f, WIDTH - MARGIN, y - 4f,
                Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
            y += LINE
        }
        try {
            startPage()
            for (fields in rows) {
                require(fields.size == headings.size) { "Invalid PDF column count" }
                val cells = fields.map { wrapped(it, text, col - 9) }
                for (line in 0 until cells.maxOf { it.size }) {
                    if (y + LINE > BOTTOM) startPage()
                    cells.forEachIndexed { index, cell ->
                        cell.getOrNull(line)?.let { draw(it, MARGIN + index * col, y, text, col - 9) }
                    }
                    y += LINE
                }
                y += 4f
            }
            endPage()
            pdf.writeTo(destination)
            destination.flush()
        } finally {
            if (page != null) endPage()
            pdf.close()
        }
    }
}
