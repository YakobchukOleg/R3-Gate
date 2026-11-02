package com.r3gate.app

/** Two independent accurate GPS fixes must agree before entering or leaving a geozone. */
object GpsArrivalRules {
    data class State(val confirmedId:String?=null,val candidateId:String?=null,val candidateSinceMs:Long=0L,val lastFixMs:Long=0L)
    data class Decision(val state:State,val event:String?=null,val placeId:String?=null)
    fun advance(state:State,observedId:String?,fixMs:Long,dwellMs:Long=60_000L):Decision {
        if(fixMs<=state.lastFixMs || dwellMs<0) return Decision(state)
        if(observedId==state.confirmedId) return Decision(state.copy(candidateId=null,candidateSinceMs=0,lastFixMs=fixMs))
        val candidateMatches=state.candidateSinceMs>0 && state.candidateId==observedId
        if(!candidateMatches) return Decision(state.copy(candidateId=observedId,candidateSinceMs=fixMs,lastFixMs=fixMs))
        if(fixMs-state.candidateSinceMs<dwellMs) return Decision(state.copy(lastFixMs=fixMs))
        val event=if(observedId==null) "DEPARTURE" else "ARRIVAL"
        return Decision(State(confirmedId=observedId,lastFixMs=fixMs),event,observedId ?: state.confirmedId)
    }
}
