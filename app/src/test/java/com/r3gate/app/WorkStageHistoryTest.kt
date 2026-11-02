package com.r3gate.app

import org.junit.Assert.*
import org.junit.Test

class WorkStageHistoryTest {
    private fun s(a:Long,b:Long,kind:String="Obiekt",id:String="o") =
        WorkStageHistory.Stage(a,b,kind,id)

    @Test fun contiguousStagesKeepOriginalCategoriesAndObjects() {
        val result=WorkStageHistory.inspect(100,400,listOf(s(100,200),s(200,300,"Przejazd",""),s(300,400,"Zakupy","")))
        assertEquals(3,result.slices.size)
        assertEquals(listOf("Obiekt","Przejazd","Zakupy"),result.slices.map { it.kind })
        assertEquals(0L,result.gapMillis)
        assertEquals(0L,result.overlapMillis)
    }

    @Test fun gapsAreReportedWithoutInventingTime() {
        val result=WorkStageHistory.inspect(100,400,listOf(s(120,200),s(250,350)))
        assertEquals(120L,result.gapMillis)
        assertEquals(180L,result.slices.sumOf { it.end-it.start })
    }

    @Test fun overlapsAreReportedRatherThanDoubleCounted() {
        val result=WorkStageHistory.inspect(100,300,listOf(s(100,250),s(200,300,"Przejazd","")))
        assertEquals(50L,result.overlapMillis)
        assertEquals(150L,result.slices.sumOf { it.end-it.start })
    }

    @Test fun stagesAreClippedToShiftBoundaries() {
        val result=WorkStageHistory.inspect(100,200,listOf(s(0,150),s(150,300)))
        assertEquals(100L,result.slices.sumOf { it.end-it.start })
        assertEquals(0L,result.gapMillis)
    }

    @Test fun invalidIntervalsAreCountedWithoutModifyingHistory() {
        val result=WorkStageHistory.inspect(100,200,listOf(s(150,150),s(180,120),s(100,200)))
        assertEquals(2,result.invalidStages)
        assertEquals(100L,result.slices.sumOf { it.end-it.start })
        assertEquals(3,listOf(s(150,150),s(180,120),s(100,200)).size)
    }
}
