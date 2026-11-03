package com.r3gate.app

/**
 * Pure UI state for Obiekty filters. Switching archives deliberately clears
 * city selection, so stale chips cannot silently hide archived records.
 */
object ObjectFilterUiState {
    data class State(
        val archived: Boolean = false,
        val grouped: Boolean = true,
        val city: String? = null
    )

    fun toggleArchive(state: State): State =
        state.copy(archived = !state.archived, city = null)

    fun toggleGrouping(state: State): State =
        state.copy(grouped = !state.grouped)

    fun city(state: State, choice: String?): State =
        if (state.archived) state.copy(city = null)
        else state.copy(city = choice?.trim()?.takeIf { it.isNotBlank() })

    fun restore(state: State, availableCities: List<String>): State =
        if (state.archived || state.city != null &&
            availableCities.none { it.equals(state.city, ignoreCase = true) })
            state.copy(city = null)
        else state

    fun status(state: State): String =
        if (state.archived) "Archiwum" else if (state.city != null) "Miasto: " + state.city
        else "Wszystkie miasta"
}
