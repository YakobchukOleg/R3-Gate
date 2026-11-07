package com.r3gate.app
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.time.Instant
class DiagnosticLogStoreTest {
    @get:Rule val temp=TemporaryFolder()
    @Test fun retainsPreviousDaysAcrossNewInstances() {
        DiagnosticLogStore(temp.root).append(1000,"{\"kind\":\"manual\"}")
        assertTrue(DiagnosticLogStore(temp.root).snapshot().contains("manual"))
    }
    @Test fun expiresOnlyOwnedDailyFiles() {
        val unrelated=java.io.File(temp.root,"keep.txt").apply { writeText("keep") }
        val store=DiagnosticLogStore(temp.root)
        for(day in 1..5) store.append(Instant.parse("2026-11-0${day}T12:00:00Z").toEpochMilli(),"{\"day\":$day}")
        val report=store.snapshot();assertFalse(report.contains("day\":1"));assertTrue(report.contains("day\":3"));assertTrue(unrelated.exists())
    }
    @Test fun rejectsMultilinePayload() { assertThrows(IllegalArgumentException::class.java) { DiagnosticLogStore(temp.root).append(1000,"one\ntwo") } }
    @Test fun boundedLogMarksTruncationAndKeepsLatest() {
        val store=DiagnosticLogStore(temp.root,200)
        for(i in 1..20) store.append(1000,"{\"event\":$i,\"value\":\"abcdefghijklmnop\"}")
        assertTrue(store.snapshot().contains("DIAGNOSTICS_TRUNCATED"));assertTrue(store.snapshot().contains("event\":20"))
    }
    @Test fun importedHistoryIsSeparateFromCurrentLogs() {
        val store=DiagnosticLogStore(temp.root);store.importHistory("{\"installation\":\"old\"}\n".toByteArray());store.append(1000,"{\"installation\":\"new\"}")
        assertTrue(store.snapshot().contains("old"));assertTrue(store.snapshot().contains("new"))
    }
}
