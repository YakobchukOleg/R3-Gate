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
        val pdf = PdfDocument()
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

        fun clipped(value: String, paint: Paint): String {
            val clean = value.replace('\n', ' ').replace('\r', ' ')
            if (paint.measureText(clean) <= col - 9) return clean
            val count = paint.breakText(clean, true, col - 24, null)
            return clean.take(count.coerceAtLeast(0)) + "…"
        }
        fun endPage() {
            val current = page ?: return
            canvas?.drawText("$footer  •  Strona $number", MARGIN, HEIGHT - 25f, text)
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
            canvas!!.drawText(title, MARGIN, y, bold.apply { textSize = 17f })
            bold.textSize = 10f
            y += 28f
            headings.forEachIndexed { index, label ->
                canvas!!.drawText(clipped(label, bold), MARGIN + index * col, y, bold)
            }
            y += 13f
            canvas!!.drawLine(MARGIN, y, WIDTH - MARGIN, y,
                Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
            y += LINE
        }
        try {
            startPage()
            for (fields in rows) {
                require(fields.size == headings.size) { "Invalid PDF column count" }
                if (y + LINE > BOTTOM) startPage()
                fields.forEachIndexed { index, value ->
                    canvas!!.drawText(clipped(value, text), MARGIN + index * col, y, text)
                }
                y += LINE
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
