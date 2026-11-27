package com.streakcounter.core.domain

import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/** The phone's current local date. Implementations re-read it on [refresh] (midnight, time-zone or clock change). */
interface Today {
    val date: StateFlow<LocalDate>
    fun refresh()
}
