package com.r3gate.app.backup

import com.r3gate.app.WorkPlaceRules
import com.r3gate.app.WorkTimeRounding
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
    enum class Report { WORK_TIME, BY_OBJECT, MATERIALS, STAGES, OBJECT_HISTORY }
    data class Table(val headers: List<String>, val rows: List<List<String>>)
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
        val table = table(json, report, period)
        WorkMeterCsv.write(destination, table.headers, table.rows.asSequence())
    }

    fun table(json: String, report: Report, period: Period): Table {
        val data = JSONObject(json)
        val objects = list(data, "objects").filter { it.optString("id").isNotBlank() }
            .groupBy { it.optString("id") }.mapNotNull { (id, matches) ->
                matches.singleOrNull()?.let { id to it }
            }.toMap()
        val names = objects.mapValues { (_, obj) ->
            val title = listOf("name", "title", "organization", "Opis")
                .map { obj.optString(it) }.firstOrNull(String::isNotBlank).orEmpty()
            listOf(obj.optString("TAG"), title).filter(String::isNotBlank).distinct().joinToString(" · ")
        }
        fun address(id: String): String {
            val obj = objects[id] ?: return ""
            val street = obj.optString("Adres").ifBlank {
                listOf("street", "building", "postcode").map { obj.optString(it) }
                    .filter(String::isNotBlank).joinToString(" ")
            }
            return listOf(obj.optString("city"), street).filter(String::isNotBlank).joinToString(", ")
        }
        fun name(id: String, fallback: String) = names[id]?.takeIf { it.isNotBlank() } ?: fallback
        fun clipped(start: Long, end: Long): Pair<Long,Long>? {
            require(end >= start) { "Negative duration" }
            if (start == end) return if (period.includes(day(start))) start to end else null
            val lower = period.from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE
            val upper = period.to?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
            if (end <= lower || start >= upper) return null
            return maxOf(start,lower) to minOf(end,upper)
        }
        fun stageBounds(shift: JSONObject, stage: JSONObject, start: Long, end: Long): Pair<Long, Long>? {
            val from = millis(stage, "start")
            val to = millis(stage, "end")
            val recordedStart = shift.optLong("recordedStart", start)
            // Rounding retains the actual stage start as evidence before the accounting boundary.
            val earliest = if (recordedStart in 1..start && WorkTimeRounding.ceil(recordedStart) == start)
                recordedStart else start
            require(to >= from && from >= earliest && to <= end) { "Invalid stage duration" }
            if (to <= start) return null
            return clipped(maxOf(start, from), to)
        }
        return when (report) {
            Report.WORK_TIME -> {
                val rows = list(data, "shifts").mapNotNull { shift ->
                    val start = millis(shift, "start")
                    val end = millis(shift, "end")
                    clipped(start,end)?.let { (from,to) ->
                        val id = shift.optString("objectId")
                        listOf(day(from).toString(), stamp(from), stamp(to), hours(to - from),
                            id, name(id, shift.optString("object")))
                    }
                }
                Table(listOf("Data", "Początek", "Koniec", "Godziny przedziału (brutto)", "ID obiektu", "Obiekt"), rows)
            }
            Report.BY_OBJECT -> {
                val totals = linkedMapOf<Pair<String, String>, Long>()
                for (shift in list(data, "shifts")) {
                    val start = millis(shift, "start")
                    val end = millis(shift, "end")
                    require(end >= start) { "Negative duration" }
                    if (clipped(start,end) == null) continue
                    val stages = list(shift, "stages")
                    if (stages.isEmpty()) {
                        if (!WorkPlaceRules.isOnSiteStage(shift.optString("kind", "Obiekt"))) continue
                        val (from,to) = requireNotNull(clipped(start,end))
                        val id = shift.optString("objectId")
                        val key = id to name(id, shift.optString("object"))
                        totals[key] = Math.addExact(totals[key] ?: 0,to - from)
                    } else for (stage in stages) {
                        val interval = stageBounds(shift, stage, start, end) ?: continue
                        if (!WorkPlaceRules.isOnSiteStage(stage.optString("kind", "Obiekt"))) continue
                        val (from, to) = interval
                        val id = stage.optString("objectId")
                        val key = id to name(id, stage.optString("object"))
                        totals[key] = Math.addExact(totals[key] ?: 0,to - from)
                    }
                }
                Table(listOf("ID obiektu", "Obiekt", "Godziny na obiekcie"), totals.map { (key, value) -> listOf(key.first, key.second, hours(value)) })
            }
            Report.STAGES, Report.OBJECT_HISTORY -> {
                val objectHistory = report == Report.OBJECT_HISTORY
                val rows = list(data, "shifts").flatMap { shift ->
                    val start = millis(shift, "start")
                    val end = millis(shift, "end")
                    if (clipped(start, end) == null) emptyList() else {
                        val stages = list(shift, "stages").ifEmpty {
                            val assigned = shift.optString("objectId").isNotBlank() || shift.optString("object").isNotBlank()
                            val kind = shift.optString("kind").ifBlank {
                                if (objectHistory && assigned) "Obiekt" else "Nieprzypisane"
                            }
                            listOf(JSONObject(shift.toString()).put("kind", kind))
                        }
                        stages.mapNotNull { stage ->
                            val (from, to) = stageBounds(shift, stage, start, end) ?: return@mapNotNull null
                            val kind = stage.optString("kind").ifBlank { "Nieprzypisane" }
                            val id = stage.optString("objectId")
                            val label = name(id, stage.optString("object"))
                            if (objectHistory && (!WorkPlaceRules.isOnSiteStage(kind) ||
                                    (id.isBlank() && label.isBlank()))) return@mapNotNull null
                            val detail = if (objectHistory) address(id) else
                                listOf(stage.optString("fromObject"), stage.optString("toObject"))
                                    .takeIf { it.any(String::isNotBlank) }?.joinToString(" → ").orEmpty()
                            listOf(kind, stamp(from), stamp(to), hours(to - from), id, label, detail)
                        }
                    }
                }
                val lastColumn = if (objectHistory) "Adres" else "Trasa"
                Table(listOf("Etap", "Początek", "Koniec", "Godziny", "ID obiektu", "Obiekt", lastColumn), rows)
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
                        val quantity = item.optString("quantity", item.optString("Ilość")).trim()
                        val amount = quantity.replace(',', '.').toBigDecimalOrNull()
                        require(amount != null && amount.signum() > 0) { "Invalid material quantity" }
                        listOf(raw, id, name(id, item.optString("object")),
                            item.optString("name", item.optString("Materiał")),
                            quantity,
                            item.optString("unit"))
                    }
                }
                Table(listOf("Data", "ID obiektu", "Obiekt", "Materiał", "Ilość", "Jednostka"), rows)
            }
        }
    }
}
