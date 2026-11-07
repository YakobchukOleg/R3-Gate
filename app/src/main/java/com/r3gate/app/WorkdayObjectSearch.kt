package com.r3gate.app

internal object WorkdayObjectSearch {
    private const val MAX_FIX_AGE_MS = 30_000L

    fun matchesCity(city: String, query: String, requireQuery: Boolean): Boolean =
        (!requireQuery || query.isNotBlank()) && ObjectSearch.matches(city, query)

    fun suggestedId(point: GpsWorkRules.Point?, places: List<GpsWorkRules.Place>, now: Long): String? {
        if (point == null || !GpsLocationQuality.acceptable(now, point.timestampMs,
                point.accuracyM, point.latitude, point.longitude) ||
            now - point.timestampMs > MAX_FIX_AGE_MS) return null
        return GpsWorkRules.nearest(point, places)?.place?.id
    }
}
