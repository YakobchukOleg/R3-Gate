package com.r3gate.app

internal enum class WorkMeterHomeState(val actionLabel: String) {
    CLOSED("Rozpocznij dzień"),
    ON_SITE("Rozpocznij przejazd"),
    TRAVEL("Potwierdź przyjazd"),
    SHOPPING("Zakończ zakupy"),
    PERSONAL("Wznów pracę"),
    UNASSIGNED("Wybierz obiekt");

    companion object {
        fun from(dayOpen: Boolean, kind: String, objectId: String): WorkMeterHomeState = when {
            !dayOpen -> CLOSED
            kind == "Przejazd" -> TRAVEL
            kind == "Zakupy" -> SHOPPING
            kind == "Prywatnie" -> PERSONAL
            (kind.isBlank() || WorkPlaceRules.isOnSiteStage(kind)) && objectId.isNotBlank() -> ON_SITE
            else -> UNASSIGNED
        }

        fun stageElapsed(now: Long, dayStart: Long, stageStart: Long): Long {
            if (dayStart <= 0 || now < dayStart) return 0
            return now - (if (stageStart <= 0) dayStart else stageStart.coerceIn(dayStart, now))
        }
    }
}
