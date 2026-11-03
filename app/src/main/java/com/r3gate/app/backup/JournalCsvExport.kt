package com.r3gate.app.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Read-only reports from existing JSON; never changes journal, wages, or stage types. */
object JournalCsvExport {
    enum class Report { WORK_TIME, BY_OBJECT, MATERIALS }
    data class Period(val from: LocalDate? = null, val to: LocalDate? = null) {
        init { require(from == null || to == null || !from.isAfter(to)) }
        fun includes(day: LocalDate) = (from == null || !day.isBefore(from)) &&
            (to == null || !day.isAfter(to))
    }
    private val zone = ZoneId.of("Europe/Warsaw")
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
    private fun list(data: JSONObject, key: String): List<JSONObject> {
        if (data.has(key)) require(data.optJSONArray(key) != null) { "Invalid array $key" }
        val array = data.optJSONArray(key) ?: JSONArray()
        return (0 until array.length()).map { array.optJSONObject(it)
            ?: throw IllegalArgumentException("Invalid record $key[$it]") }
    }
    private fun millis(record: JSONObject, field: String): Long {
        require(record.has(field) && record.optLong(field, -1) > 0) { "Invalid $field" }
        return record.getLong(field)
    }
    private fun day(time: Long) = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    private fun stamp(time: Long) = fmt.format(Instant.ofEpochMilli(time))
    private fun hours(millis: Long) = BigDecimal.valueOf(millis)
        .divide(BigDecimal.valueOf(3600000), 2, RoundingMode.HALF_UP).toPlainString()

    fun write(json: String, report: Report, period: Period, destination: OutputStream) {
        val data = JSONObject(json)
        val names = list(data, "objects").associate {
            it.optString("id") to it.optString("name", it.optString("title"))
        }
        fun name(id: String, fallback: String) = names[id]?.takeIf { it.isNotBlank() } ?: fallback
        when (report) {
            Report.WORK_TIME -> {
                val rows = list(data, "shifts").mapNotNull { shift ->
                    val start = millis(shift, "start")
                    val end = millis(shift, "end")
                    require(end >= start) { "Negative duration" }
                    if (!period.includes(day(start))) null else {
                        val id = shift.optString("objectId")
                        listOf(day(start).toString(), stamp(start), stamp(end), hours(end - start),
                            id, name(id, shift.optString("object")))
                    }
                }
                WorkMeterCsv.write(destination,
                    listOf("Data", "Początek", "Koniec", "Godziny", "ID obiektu", "Obiekt"),
                    rows.asSequence())
            }
            Report.BY_OBJECT -> {
                val totals = linkedMapOf<Pair<String, String>, Long>()
                for (shift in list(data, "shifts")) {
                    val start = millis(shift, "start")
                    val end = millis(shift, "end")
                    require(end >= start) { "Negative duration" }
                    if (!period.includes(day(start))) continue
                    val stages = list(shift, "stages")
                    if (stages.isEmpty()) {
                        val id = shift.optString("objectId")
                        val key = id to name(id, shift.optString("object"))
                        totals[key] = (totals[key] ?: 0) + end - start
                    } else for (stage in stages) {
                        val a = millis(stage, "start")
                        val b = millis(stage, "end")
                        require(b >= a && a >= start && b <= end) { "Invalid stage duration" }
                        if (stage.optString("kind", "Obiekt") != "Obiekt") continue
                        val id = stage.optString("objectId")
                        val key = id to name(id, stage.optString("object"))
                        totals[key] = (totals[key] ?: 0) + b - a
                    }
                }
                WorkMeterCsv.write(destination,
                    listOf("ID obiektu", "Obiekt", "Godziny na obiekcie"),
                    totals.asSequence().map { (key, value) ->
                        listOf(key.first, key.second, hours(value))
                    })
            }
            Report.MATERIALS -> {
                val rows = list(data, "materials").mapNotNull { item ->
                    val raw = item.optString("date")
                    val date = if (raw.isBlank()) null else try { LocalDate.parse(raw.take(10)) }
                        catch (e: Exception) { throw IllegalArgumentException("Invalid material date", e) }
                    if (date != null && !period.includes(date) ||
                        date == null && (period.from != null || period.to != null)) null
                    else {
                        val id = item.optString("objectId")
                        listOf(raw, id, name(id, item.optString("object")),
                            item.optString("name", item.optString("Materiał")),
                            item.optString("quantity", item.optString("Ilość")),
                            item.optString("unit"))
                    }
                }
                WorkMeterCsv.write(destination,
                    listOf("Data", "ID obiektu", "Obiekt", "Materiał", "Ilość", "Jednostka"),
                    rows.asSequence())
            }
        }
    }
}
