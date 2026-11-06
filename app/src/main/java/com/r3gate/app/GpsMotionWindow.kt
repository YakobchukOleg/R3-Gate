package com.r3gate.app

/** Orientation-independent rolling evidence; one weak sample does not erase walking. */
internal class GpsMotionWindow {
    data class Evidence(val rms: Double, val activeFraction: Double, val samples: Int, val wake: Boolean)
    private val samples=ArrayDeque<Pair<Long,Double>>()
    private var lastWake=0L
    fun observe(elapsedMs: Long, delta: Double): Evidence {
        if(elapsedMs<=0 || !delta.isFinite() || delta<0) return Evidence(0.0,0.0,0,false)
        if(samples.isNotEmpty() && elapsedMs<samples.last().first) { samples.clear(); lastWake=0 }
        if(samples.isNotEmpty() && elapsedMs-samples.last().first<100) return Evidence(0.0,0.0,samples.size,false)
        samples.addLast(elapsedMs to delta.coerceAtMost(20.0))
        while(samples.isNotEmpty() && elapsedMs-samples.first().first>5_000) samples.removeFirst()
        val rms=kotlin.math.sqrt(samples.sumOf { it.second*it.second }/samples.size)
        val fraction=samples.count { it.second>=0.35 }.toDouble()/samples.size
        val wake=samples.size>=8 && elapsedMs-samples.first().first>=3_000 &&
            rms>=0.45 && fraction>=0.25 && (lastWake==0L || elapsedMs-lastWake>=90_000)
        if(wake) lastWake=elapsedMs
        return Evidence(rms,fraction,samples.size,wake)
    }
}

internal object GpsMotionProbe {
    const val DURATION_MS=60_000L
    const val REARM_MS=90_000L
    fun mayStart(now:Long,last:Long):Boolean=now>0 && (last<=0 || now-last>=REARM_MS)
    fun interval(now:Long,start:Long,baseline:Long):Long=
        if(start>0 && now>=start && now-start<DURATION_MS) minOf(baseline,3_000L) else baseline
}
