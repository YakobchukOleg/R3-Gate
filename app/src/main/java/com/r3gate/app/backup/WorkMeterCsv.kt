package com.r3gate.app.backup

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Offline, streaming spreadsheet export. Does not read or change application storage.
 * All rows must have exactly the number of fields defined in the header.
 */
object WorkMeterCsv {
    private const val MAX_COLUMNS = 128
    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

    fun suggestedName(type: String, date: LocalDate): String {
        require(type.matches(Regex("[A-Za-z0-9_-]{1,40}"))) { "Invalid export type" }
        return "WorkMeter-${type}-${date.format(dateFormat)}.csv"
    }

    fun write(
        destination: OutputStream,
        headers: List<String>,
        rows: Sequence<List<String>>,
        excelBom: Boolean = true,
        separator: Char = ';'
    ) {
        require(headers.isNotEmpty() && headers.size <= MAX_COLUMNS) { "Invalid columns" }
        require(headers.none { it.isBlank() }) { "Empty column heading" }
        require(separator == ';' || separator == ',') { "Unsupported CSV separator" }
        val writer = OutputStreamWriter(destination, Charsets.UTF_8)
        if (excelBom) writer.write("\uFEFF")
        fun emit(fields: List<String>) {
            require(fields.size == headers.size) { "CSV column count mismatch" }
            writer.write(fields.joinToString(separator.toString()) { escape(it, separator) })
            writer.write("\r\n")
        }
        emit(headers)
        rows.forEach { emit(it) }
        writer.flush()
    }

    /**
     * Guard against spreadsheet formula execution while keeping CSV quoting correct.
     * User-controlled fields starting with =,+,-,@ or leading whitespace before those
     * characters are prefixed with an apostrophe, including tabs/newlines.
     */
    fun escape(value: String, separator: Char = ';'): String {
        require(separator == ';' || separator == ',') { "Unsupported CSV separator" }
        val suspicious = value.trimStart().firstOrNull() in setOf('=', '+', '-', '@')
        val safe = if (suspicious) "'$value" else value
        return if (safe.any { it == separator || it == '"' || it == '\r' || it == '\n' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else safe
    }
}
